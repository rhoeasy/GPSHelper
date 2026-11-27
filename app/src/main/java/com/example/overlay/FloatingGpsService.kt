package com.example.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.MainActivity
import com.example.R
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.GpsRepository
import com.example.sensor.GForceData
import com.example.sensor.GForceRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground location service hosting a clean, draggable native View floating HUD pill
 * displaying ONLY real-time Altitude and live G-Force diagram (with Day/Night/Auto sync).
 */
class FloatingGpsService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var notificationManager: NotificationManager? = null
    private var overlayRoot: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var gForceMeterView: GForceMeterView? = null
    private var altitudeRowText: TextView? = null
    private var gForceRowText: TextView? = null
    private var statusDotView: View? = null
    private var closeButtonView: TextView? = null

    private var isMiniMode = false
    private var lastNotificationUpdateMs = 0L
    private var latestGpsData: GpsData = GpsRepository.gpsData.value
    private var latestGForceData: GForceData = GForceRepository.gForceData.value
    private var latestThemeMode: ThemeMode = GpsRepository.themeMode.value

    private var monoTypeface: Typeface = Typeface.MONOSPACE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        monoTypeface = runCatching {
            ResourcesCompat.getFont(this, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        }.getOrDefault(Typeface.MONOSPACE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this) || !GpsRepository.hasLocationPermission(this)) {
            Log.w(TAG, "Cannot start floating overlay: missing overlay or location permission")
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(
            gpsData = GpsRepository.gpsData.value,
            gForce = GForceRepository.gForceData.value
        )

        try {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgsType)
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground location service", se)
            stopSelf()
            return START_NOT_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter foreground state", e)
            stopSelf()
            return START_NOT_STICKY
        }

        GpsRepository.setOverlayRunning(true)
        GpsRepository.startTracking(applicationContext, CLIENT_ID)
        GForceRepository.startListening(applicationContext, CLIENT_ID)

        if (overlayRoot == null) {
            attachOverlayWindow()
            observeTelemetryStreams()
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val root = overlayRoot ?: return
        val params = layoutParams ?: return
        renderOverlay(latestGpsData, latestGForceData, latestThemeMode)
        clampPosition(params, root)
        runCatching { windowManager?.updateViewLayout(root, params) }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlayWindow()
        GpsRepository.stopTracking(CLIENT_ID)
        GForceRepository.stopListening(CLIENT_ID)
        GpsRepository.setOverlayRunning(false)
        super.onDestroy()
    }

    private fun observeTelemetryStreams() {
        serviceScope.launch {
            combine(
                GpsRepository.gpsData,
                GForceRepository.gForceData,
                GpsRepository.themeMode
            ) { gps, gForce, theme ->
                HudStreamState(gps, gForce, theme)
            }.collect { state ->
                latestGpsData = state.gps
                latestGForceData = state.gForce
                latestThemeMode = state.theme
                renderOverlay(state.gps, state.gForce, state.theme)
                maybeUpdateNotification(state.gps, state.gForce)
            }
        }
    }

    private data class HudStreamState(
        val gps: GpsData,
        val gForce: GForceData,
        val theme: ThemeMode
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpF(value: Float): Float = value * resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlayWindow() {
        val container = FrameLayout(this)

        val pillBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(20f)
            setColor(COLOR_PILL_BG_DARK)
            setStroke(dp(1).coerceAtLeast(1), COLOR_PILL_BORDER_DARK)
        }
        container.background = pillBackground
        container.elevation = dpF(6f)

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(26), dp(8))
        }

        // Left: Live G-Force circular diagram
        val gMeter = GForceMeterView(this)
        val meterSize = dp(60)
        mainRow.addView(
            gMeter,
            LinearLayout.LayoutParams(meterSize, meterSize).apply {
                marginEnd = dp(10)
            }
        )
        gForceMeterView = gMeter

        // Right: Altitude + G-Force numerical readouts
        val infoColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val altRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COLOR_STATUS_AMBER_DARK)
            }
        }
        val dotSize = dp(7)
        altRow.addView(
            dot,
            LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = dp(6)
            }
        )
        statusDotView = dot

        val altText = TextView(this).apply {
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            maxLines = 1
            includeFontPadding = false
        }
        altRow.addView(
            altText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        altitudeRowText = altText

        infoColumn.addView(
            altRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val gText = TextView(this).apply {
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            maxLines = 1
            includeFontPadding = false
            setPadding(dp(13), dp(4), 0, 0)
        }
        infoColumn.addView(
            gText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        gForceRowText = gText

        mainRow.addView(
            infoColumn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            mainRow,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Subtle × close button in top-right corner
        val closeButton = TextView(this).apply {
            text = "×"
            contentDescription = getString(R.string.overlay_close_cd)
            setTextColor(COLOR_CLOSE_DARK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(4), dp(2), dp(4), dp(4))
            setOnClickListener {
                stopSelf()
            }
        }
        closeButtonView = closeButton
        container.addView(
            closeButton,
            FrameLayout.LayoutParams(
                dp(24),
                dp(24),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(2)
                marginEnd = dp(2)
            }
        )

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(96)
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        container.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        clampPosition(params, view)
                        runCatching { windowManager?.updateViewLayout(view, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        isMiniMode = !isMiniMode
                        applyModeSizing()
                        renderOverlay(latestGpsData, latestGForceData, latestThemeMode)
                        clampPosition(params, view)
                        runCatching { windowManager?.updateViewLayout(view, params) }
                    }
                    true
                }

                else -> false
            }
        }

        try {
            windowManager?.addView(container, params)
            overlayRoot = container
            layoutParams = params
            applyModeSizing()
            renderOverlay(latestGpsData, latestGForceData, latestThemeMode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach floating window view", e)
            stopSelf()
        }
    }

    private fun applyModeSizing() {
        val meter = gForceMeterView ?: return
        val gRow = gForceRowText ?: return
        val targetSize = if (isMiniMode) dp(38) else dp(60)
        val lp = meter.layoutParams as? LinearLayout.LayoutParams
        if (lp != null && (lp.width != targetSize || lp.height != targetSize)) {
            lp.width = targetSize
            lp.height = targetSize
            lp.marginEnd = if (isMiniMode) dp(8) else dp(10)
            meter.layoutParams = lp
        }
        gRow.visibility = if (isMiniMode) View.GONE else View.VISIBLE
    }

    private fun isOverlayDark(themeMode: ThemeMode): Boolean {
        return when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> {
                val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun renderOverlay(
        gps: GpsData,
        gForce: GForceData,
        themeMode: ThemeMode
    ) {
        val altView = altitudeRowText ?: return
        val gView = gForceRowText ?: return
        val dot = statusDotView
        val dark = isOverlayDark(themeMode)

        // Update pill container colors cleanly for Day / Night
        val bgDrawable = overlayRoot?.background as? GradientDrawable
        val borderPx = dp(1).coerceAtLeast(1)
        bgDrawable?.setColor(if (dark) COLOR_PILL_BG_DARK else COLOR_PILL_BG_LIGHT)
        bgDrawable?.setStroke(borderPx, if (dark) COLOR_PILL_BORDER_DARK else COLOR_PILL_BORDER_LIGHT)
        closeButtonView?.setTextColor(if (dark) COLOR_CLOSE_DARK else COLOR_CLOSE_LIGHT)

        // Update G-Force Canvas view
        gForceMeterView?.updateState(gForce, dark)

        val accentColor = if (dark) COLOR_ACCENT_DARK else COLOR_ACCENT_LIGHT
        val primaryTextColor = if (dark) COLOR_TEXT_PRIMARY_DARK else COLOR_TEXT_PRIMARY_LIGHT
        val secondaryTextColor = if (dark) COLOR_TEXT_SECONDARY_DARK else COLOR_TEXT_SECONDARY_LIGHT
        val dividerColor = if (dark) COLOR_DIVIDER_DARK else COLOR_DIVIDER_LIGHT

        val dotColor = when (gps.fixStatus) {
            GpsFixStatus.LOCKED -> if (dark) COLOR_STATUS_EMERALD_DARK else COLOR_STATUS_EMERALD_LIGHT
            GpsFixStatus.FUSED_FALLBACK -> accentColor
            GpsFixStatus.SEARCHING -> if (dark) COLOR_STATUS_AMBER_DARK else COLOR_STATUS_AMBER_LIGHT
            GpsFixStatus.DISABLED, GpsFixStatus.NO_PERMISSION -> if (dark) COLOR_STATUS_RED_DARK else COLOR_STATUS_RED_LIGHT
        }
        (dot?.background as? GradientDrawable)?.setColor(dotColor)

        val altText = if (gps.hasFix && gps.altitudeMeters != null) {
            String.format(Locale.US, "%.1f m", gps.altitudeMeters)
        } else {
            "-- m"
        }
        val currentGStr = String.format(Locale.US, "%.2fG", gForce.totalG)
        val peakGStr = String.format(Locale.US, "%.2fG", gForce.peakG)

        val gColor = when {
            gForce.totalG >= 1.0f -> if (dark) COLOR_STATUS_RED_DARK else COLOR_STATUS_RED_LIGHT
            gForce.totalG >= 0.5f -> if (dark) COLOR_STATUS_AMBER_DARK else COLOR_STATUS_AMBER_LIGHT
            else -> accentColor
        }

        if (isMiniMode) {
            // Mini single-row pill: [G-Force dial] + Altitude + G value
            val sb = SpannableStringBuilder()
            val altStart = sb.length
            sb.append(altText)
            sb.setSpan(ForegroundColorSpan(primaryTextColor), altStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), altStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val sepStart = sb.length
            sb.append(" · ")
            sb.setSpan(ForegroundColorSpan(dividerColor), sepStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val gStart = sb.length
            sb.append(currentGStr)
            sb.setSpan(ForegroundColorSpan(gColor), gStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), gStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.92f), gStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            altView.text = sb
        } else {
            // Compact mode: Row 1 = Altitude, Row 2 = Live G + Peak G
            val altSb = SpannableStringBuilder()
            val labelStart = altSb.length
            altSb.append("海拔 ")
            altSb.setSpan(ForegroundColorSpan(secondaryTextColor), labelStart, altSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            altSb.setSpan(RelativeSizeSpan(0.8f), labelStart, altSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val valStart = altSb.length
            altSb.append(altText)
            altSb.setSpan(ForegroundColorSpan(primaryTextColor), valStart, altSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            altSb.setSpan(StyleSpan(Typeface.BOLD), valStart, altSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            altView.text = altSb

            val gSb = SpannableStringBuilder()
            val liveStart = gSb.length
            gSb.append(currentGStr)
            gSb.setSpan(ForegroundColorSpan(gColor), liveStart, gSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            gSb.setSpan(StyleSpan(Typeface.BOLD), liveStart, gSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            val peakStart = gSb.length
            gSb.append("  峰值 $peakGStr")
            gSb.setSpan(ForegroundColorSpan(secondaryTextColor), peakStart, gSb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            gView.text = gSb
        }
    }

    private fun clampPosition(params: WindowManager.LayoutParams, view: View) {
        val metrics = resources.displayMetrics
        val viewWidth = view.width.takeIf { it > 0 } ?: 220
        val viewHeight = view.height.takeIf { it > 0 } ?: 80
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun removeOverlayWindow() {
        val root = overlayRoot ?: return
        overlayRoot = null
        layoutParams = null
        gForceMeterView = null
        altitudeRowText = null
        gForceRowText = null
        statusDotView = null
        closeButtonView = null
        if (root.isAttachedToWindow) {
            runCatching { windowManager?.removeViewImmediate(root) }
                .onFailure {
                    runCatching { windowManager?.removeView(root) }
                }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun maybeUpdateNotification(gps: GpsData, gForce: GForceData) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdateMs < NOTIFICATION_THROTTLE_MS) return
        lastNotificationUpdateMs = now
        runCatching {
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(gps, gForce))
        }
    }

    private fun buildNotification(gpsData: GpsData, gForce: GForceData): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingGpsService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val altStr = if (gpsData.hasFix && gpsData.altitudeMeters != null) {
            String.format(Locale.US, "海拔 %.1f m", gpsData.altitudeMeters)
        } else {
            getString(R.string.notification_waiting_fix)
        }
        val gStr = String.format(Locale.US, "%.2f G (峰值 %.2f G)", gForce.totalG, gForce.peakG)
        val contentText = "$altStr  ·  $gStr"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hud_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .addAction(
                0,
                getString(R.string.action_stop_overlay),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        private const val TAG = "FloatingGpsService"
        private const val CLIENT_ID = "floating_gps_service"
        private const val CHANNEL_ID = "gps_overlay_hud_channel"
        private const val NOTIFICATION_ID = 1042
        private const val NOTIFICATION_THROTTLE_MS = 1_000L

        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"

        // Dark overlay theme tokens
        private const val COLOR_PILL_BG_DARK = 0xD91A1D24.toInt()
        private const val COLOR_PILL_BORDER_DARK = 0x33FFFFFF
        private const val COLOR_ACCENT_DARK = 0xFFA8C7FA.toInt()
        private const val COLOR_TEXT_PRIMARY_DARK = 0xFFE3E3E3.toInt()
        private const val COLOR_TEXT_SECONDARY_DARK = 0xFFC4C7C5.toInt()
        private const val COLOR_DIVIDER_DARK = 0x55FFFFFF
        private const val COLOR_CLOSE_DARK = 0x99FFFFFF.toInt()
        private const val COLOR_STATUS_EMERALD_DARK = 0xFF6DD58C.toInt()
        private const val COLOR_STATUS_AMBER_DARK = 0xFFFDD663.toInt()
        private const val COLOR_STATUS_RED_DARK = 0xFFF28B82.toInt()

        // Light overlay theme tokens (Google frosted white)
        private const val COLOR_PILL_BG_LIGHT = 0xEBFFFFFF.toInt()
        private const val COLOR_PILL_BORDER_LIGHT = 0x22000000
        private const val COLOR_ACCENT_LIGHT = 0xFF1A73E8.toInt()
        private const val COLOR_TEXT_PRIMARY_LIGHT = 0xFF1F1F1F.toInt()
        private const val COLOR_TEXT_SECONDARY_LIGHT = 0xFF5F6368.toInt()
        private const val COLOR_DIVIDER_LIGHT = 0x33000000
        private const val COLOR_CLOSE_LIGHT = 0x88444746.toInt()
        private const val COLOR_STATUS_EMERALD_LIGHT = 0xFF1E8E3E.toInt()
        private const val COLOR_STATUS_AMBER_LIGHT = 0xFFE37400.toInt()
        private const val COLOR_STATUS_RED_LIGHT = 0xFFD93025.toInt()

        fun start(context: Context) {
            val intent = Intent(context, FloatingGpsService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingGpsService::class.java))
        }
    }
}
