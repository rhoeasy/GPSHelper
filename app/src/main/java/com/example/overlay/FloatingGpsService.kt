package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ble.HeartRateRepository
import com.example.gps.GpsData
import com.example.gps.GpsRepository
import com.example.sensor.GForceData
import com.example.sensor.MotionSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground location service hosting the Jetpack Compose [OverlayWindow] via
 * [OverlayWindowController] (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`),
 * displaying real-time Accelerometer + Gyroscope fused G-Force and Altitude on top of other apps.
 */
class FloatingGpsService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var notificationManager: NotificationManager? = null
    private var overlayController: OverlayWindowController? = null
    private var lastNotificationUpdateMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            gForce = MotionSensorManager.gForceData.value
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
        MotionSensorManager.startListening(applicationContext, CLIENT_ID)

        if (overlayController == null) {
            val controller = OverlayWindowController(
                context = this,
                gForceFlow = MotionSensorManager.gForceData,
                gpsFlow = GpsRepository.gpsData,
                speedUnitFlow = GpsRepository.speedUnit,
                heartRateFlow = HeartRateRepository.heartRateState,
                overlayConfigFlow = GpsRepository.overlayConfig,
                themeModeFlow = GpsRepository.themeMode,
                onCloseRequested = { stopSelf() }
            )
            if (controller.attach()) {
                overlayController = controller
                observeNotificationTelemetry()
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onConfigurationChanged()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayController?.detach()
        overlayController = null
        GpsRepository.stopTracking(CLIENT_ID)
        MotionSensorManager.stopListening(CLIENT_ID)
        GpsRepository.setOverlayRunning(false)
        super.onDestroy()
    }

    private fun observeNotificationTelemetry() {
        serviceScope.launch {
            combine(
                GpsRepository.gpsData,
                MotionSensorManager.gForceData
            ) { gps, gForce -> gps to gForce }
                .collect { (gps, gForce) ->
                    maybeUpdateNotification(gps, gForce)
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
