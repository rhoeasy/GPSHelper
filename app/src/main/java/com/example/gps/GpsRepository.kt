package com.example.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.sensor.MotionSensorManager
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

enum class OverlayScale(val factor: Float, val labelZh: String) {
    SMALL(0.90f, "小"),
    MEDIUM(1.08f, "中"),
    LARGE(1.26f, "大"),
    XLARGE(1.44f, "特大")
}

/**
 * Configurable visibility, readability, appearance, and G-Force peak settings for the
 * floating [com.example.overlay.OverlayWindow].
 */
data class OverlayDisplayConfig(
    val showSpeed: Boolean = true,
    val showGForce: Boolean = true,
    val showAltitude: Boolean = true,
    val showHeartRate: Boolean = false,
    val showBearing: Boolean = false,
    val showAccuracy: Boolean = false,
    val showCoordinates: Boolean = false,
    val backgroundAlpha: Float = 0.90f,
    val scale: OverlayScale = OverlayScale.MEDIUM,
    val fontScale: Float = 1.15f,
    val showGForcePeakOnDial: Boolean = true,
    val gForcePeakHoldSeconds: Int = 60,
    val showRideTimer: Boolean = true,
    val rideMarkerIntervalMinutes: Int = 5,
    val showRideElapsedTime: Boolean = false
) {
    val enabledCount: Int
        get() = listOf(
            showSpeed,
            showGForce,
            showAltitude,
            showHeartRate,
            showBearing,
            showAccuracy,
            showCoordinates,
            showRideTimer
        ).count { it }
}

/**
 * Configurable safety warning thresholds for riding/karting (overspeed & high heart rate).
 */
data class SafetyAlertConfig(
    val speedAlertEnabled: Boolean = false,
    val speedLimitKmh: Int = 80,
    val hrAlertEnabled: Boolean = false,
    val hrLimitBpm: Int = 175,
    val vibrateOnAlert: Boolean = true
)

/**
 * Singleton GPS telemetry hub backed exclusively by native Android LocationManager
 * and GnssStatus.Callback (API 24+). Shares a single active listener across MainActivity
 * and FloatingGpsService using reference-counted client IDs.
 */
object GpsRepository {
    private const val TAG = "GpsRepository"
    private const val PREFS_NAME = "gps_hud_prefs"
    private const val KEY_SPEED_UNIT = "speed_unit"
    private const val KEY_DASHBOARD_SPEED_STYLE = "dashboard_speed_style"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_PALETTE = "theme_palette"
    private const val KEY_AUTO_FOCUS_ON_LAUNCH = "auto_focus_on_launch"
    private const val KEY_OV_SHOW_SPEED = "overlay_show_speed"
    private const val KEY_OV_SHOW_GFORCE = "overlay_show_gforce"
    private const val KEY_OV_SHOW_ALTITUDE = "overlay_show_altitude"
    private const val KEY_OV_SHOW_HR = "overlay_show_hr"
    private const val KEY_OV_SHOW_BEARING = "overlay_show_bearing"
    private const val KEY_OV_SHOW_ACCURACY = "overlay_show_accuracy"
    private const val KEY_OV_SHOW_COORDS = "overlay_show_coords"
    private const val KEY_OV_SHOW_RIDE_TIMER = "overlay_show_ride_timer"
    private const val KEY_OV_RIDE_MARKER_MINUTES = "overlay_ride_marker_minutes"
    private const val KEY_OV_SHOW_RIDE_ELAPSED = "overlay_show_ride_elapsed"
    private const val KEY_OV_BG_ALPHA = "overlay_bg_alpha"
    private const val KEY_OV_SCALE = "overlay_scale"
    private const val KEY_OV_FONT_SCALE = "overlay_font_scale"
    private const val KEY_OV_SHOW_PEAK_ON_DIAL = "overlay_show_peak_on_dial"
    private const val KEY_OV_PEAK_HOLD_SEC = "overlay_peak_hold_sec"
    private const val KEY_OV_POS_X = "overlay_pos_x"
    private const val KEY_OV_POS_Y = "overlay_pos_y"
    private const val KEY_OV_POS_X_LAND = "overlay_pos_x_land"
    private const val KEY_OV_POS_Y_LAND = "overlay_pos_y_land"
    private const val KEY_ALERT_SPEED_EN = "alert_speed_enabled"
    private const val KEY_ALERT_SPEED_KMH = "alert_speed_kmh"
    private const val KEY_ALERT_HR_EN = "alert_hr_enabled"
    private const val KEY_ALERT_HR_BPM = "alert_hr_bpm"
    private const val KEY_ALERT_VIBRATE = "alert_vibrate"

    private const val MIN_TIME_MS = 500L
    private const val MIN_DISTANCE_M = 0f
    internal const val GPS_FRESHNESS_WINDOW_MS = 8_000L
    private const val GPS_PROVIDER_GRACE_MS = 3_000L
    private const val RECONNECT_GAP_MS = 20_000L
    private const val RECONNECT_INTERVAL_MS = 60_000L

    private val lock = Any()
    private val activeClients = mutableSetOf<String>()

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var isListening = false
    private var lastGpsFixElapsedMs: Long = 0L
    private var pendingFusedFix: Location? = null
    private var subscriptionStartedMs = 0L
    private var lastReconnectMs: Long? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData: StateFlow<GpsData> = _gpsData.asStateFlow()

    private val expireLocationFix = Runnable {
        synchronized(lock) {
            expireStaleFixLocked()
        }
    }
    private val promoteFusedFix = Runnable {
        synchronized(lock) {
            if (isListening) publishPendingFusedFixLocked(SystemClock.elapsedRealtime())
        }
    }

    private val _speedUnit = MutableStateFlow(SpeedUnit.KMH)
    val speedUnit: StateFlow<SpeedUnit> = _speedUnit.asStateFlow()

    private val _dashboardSpeedStyle = MutableStateFlow(DashboardSpeedStyle.AUTO_DIAL)
    val dashboardSpeedStyle: StateFlow<DashboardSpeedStyle> = _dashboardSpeedStyle.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themePalette = MutableStateFlow(AppThemePalette.GOOGLE_BLUE)
    val themePalette: StateFlow<AppThemePalette> = _themePalette.asStateFlow()

    private val _autoFocusOnLaunch = MutableStateFlow(false)
    val autoFocusOnLaunch: StateFlow<Boolean> = _autoFocusOnLaunch.asStateFlow()

    private val _overlayConfig = MutableStateFlow(OverlayDisplayConfig())
    val overlayConfig: StateFlow<OverlayDisplayConfig> = _overlayConfig.asStateFlow()

    private val _safetyAlertConfig = MutableStateFlow(SafetyAlertConfig())
    val safetyAlertConfig: StateFlow<SafetyAlertConfig> = _safetyAlertConfig.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

    private val _satellites = MutableStateFlow<List<GnssSatelliteInfo>>(emptyList())
    val satellites: StateFlow<List<GnssSatelliteInfo>> = _satellites.asStateFlow()

    private val liveAltitudeFilter = LiveAltitudeFilter()
    private val altitudeTracker = AltitudeSessionTracker()
    private val _altitudeTelemetry = MutableStateFlow(AltitudeTelemetry())
    val altitudeTelemetry: StateFlow<AltitudeTelemetry> = _altitudeTelemetry.asStateFlow()

    private val accuracyTracker = AccuracySessionTracker()
    private val _accuracyTelemetry = MutableStateFlow(AccuracyTelemetry())
    val accuracyTelemetry: StateFlow<AccuracyTelemetry> = _accuracyTelemetry.asStateFlow()

    private var prefsLoaded = false

    fun ensurePrefsLoaded(context: Context) {
        synchronized(lock) {
            if (prefsLoaded) return
            val appCtx = context.applicationContext
            appContext = appCtx
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUnit = prefs.getString(KEY_SPEED_UNIT, SpeedUnit.KMH.name)
            _speedUnit.value = runCatching { SpeedUnit.valueOf(savedUnit ?: SpeedUnit.KMH.name) }
                .getOrDefault(SpeedUnit.KMH)
            val speedStyle = prefs.getString(KEY_DASHBOARD_SPEED_STYLE, null)
            _dashboardSpeedStyle.value = DashboardSpeedStyle.entries.firstOrNull { it.name == speedStyle } ?: DashboardSpeedStyle.AUTO_DIAL
            _keepScreenOn.value = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
            val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.AUTO.name)
            _themeMode.value = runCatching { ThemeMode.valueOf(savedTheme ?: ThemeMode.AUTO.name) }
                .getOrDefault(ThemeMode.AUTO)
            val savedPalette = prefs.getString(KEY_THEME_PALETTE, AppThemePalette.GOOGLE_BLUE.name)
            _themePalette.value = runCatching { AppThemePalette.valueOf(savedPalette ?: AppThemePalette.GOOGLE_BLUE.name) }
                .getOrDefault(AppThemePalette.GOOGLE_BLUE)
            _autoFocusOnLaunch.value = prefs.getBoolean(KEY_AUTO_FOCUS_ON_LAUNCH, false)

            val savedScale = prefs.getString(KEY_OV_SCALE, OverlayScale.MEDIUM.name)
            val scaleEnum = runCatching { OverlayScale.valueOf(savedScale ?: OverlayScale.MEDIUM.name) }
                .getOrDefault(OverlayScale.MEDIUM)
            val loadedOverlay = OverlayDisplayConfig(
                showSpeed = prefs.getBoolean(KEY_OV_SHOW_SPEED, true),
                showGForce = prefs.getBoolean(KEY_OV_SHOW_GFORCE, true),
                showAltitude = prefs.getBoolean(KEY_OV_SHOW_ALTITUDE, true),
                showHeartRate = prefs.getBoolean(KEY_OV_SHOW_HR, false),
                showBearing = prefs.getBoolean(KEY_OV_SHOW_BEARING, false),
                showAccuracy = prefs.getBoolean(KEY_OV_SHOW_ACCURACY, false),
                showCoordinates = prefs.getBoolean(KEY_OV_SHOW_COORDS, false),
                backgroundAlpha = prefs.getFloat(KEY_OV_BG_ALPHA, 0.90f).coerceIn(0.45f, 0.98f),
                scale = scaleEnum,
                fontScale = prefs.getFloat(KEY_OV_FONT_SCALE, 1.15f).coerceIn(0.85f, 1.60f),
                showGForcePeakOnDial = prefs.getBoolean(KEY_OV_SHOW_PEAK_ON_DIAL, true),
                gForcePeakHoldSeconds = prefs.getInt(KEY_OV_PEAK_HOLD_SEC, 60),
                showRideTimer = prefs.getBoolean(KEY_OV_SHOW_RIDE_TIMER, true),
                rideMarkerIntervalMinutes = prefs.getInt(KEY_OV_RIDE_MARKER_MINUTES, 5).coerceIn(1, 60),
                showRideElapsedTime = prefs.getBoolean(KEY_OV_SHOW_RIDE_ELAPSED, false)
            )
            val finalOverlay = if (loadedOverlay.enabledCount > 0) loadedOverlay else OverlayDisplayConfig()
            _overlayConfig.value = finalOverlay
            MotionSensorManager.setPeakHoldSeconds(finalOverlay.gForcePeakHoldSeconds)

            _safetyAlertConfig.value = SafetyAlertConfig(
                speedAlertEnabled = prefs.getBoolean(KEY_ALERT_SPEED_EN, false),
                speedLimitKmh = prefs.getInt(KEY_ALERT_SPEED_KMH, 80).coerceIn(20, 200),
                hrAlertEnabled = prefs.getBoolean(KEY_ALERT_HR_EN, false),
                hrLimitBpm = prefs.getInt(KEY_ALERT_HR_BPM, 175).coerceIn(110, 220),
                vibrateOnAlert = prefs.getBoolean(KEY_ALERT_VIBRATE, true)
            )
            prefsLoaded = true
        }
    }

    fun setSpeedUnit(context: Context, unit: SpeedUnit) {
        ensurePrefsLoaded(context)
        _speedUnit.value = unit
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SPEED_UNIT, unit.name)
            .apply()
    }

    fun toggleSpeedUnit(context: Context) {
        val next = if (_speedUnit.value == SpeedUnit.KMH) SpeedUnit.MPH else SpeedUnit.KMH
        setSpeedUnit(context, next)
    }

    fun setKeepScreenOn(context: Context, enabled: Boolean) {
        ensurePrefsLoaded(context)
        _keepScreenOn.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SCREEN_ON, enabled)
            .apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        ensurePrefsLoaded(context)
        _themeMode.value = mode
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }

    fun setDashboardSpeedStyle(context: Context, style: DashboardSpeedStyle) {
        ensurePrefsLoaded(context)
        _dashboardSpeedStyle.value = style
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DASHBOARD_SPEED_STYLE, style.name).apply()
    }

    fun setThemePalette(context: Context, palette: AppThemePalette) {
        ensurePrefsLoaded(context)
        _themePalette.value = palette
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_PALETTE, palette.name)
            .apply()
    }

    fun setAutoFocusOnLaunch(context: Context, enabled: Boolean) {
        ensurePrefsLoaded(context)
        _autoFocusOnLaunch.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FOCUS_ON_LAUNCH, enabled)
            .apply()
    }

    /**
     * Updates the floating overlay content config. Returns false without applying if the
     * resulting config would disable every visible module.
     */
    fun updateOverlayConfig(
        context: Context,
        transform: (OverlayDisplayConfig) -> OverlayDisplayConfig
    ): Boolean {
        ensurePrefsLoaded(context)
        val rawNext = transform(_overlayConfig.value)
        if (rawNext.enabledCount == 0) return false
        val next = rawNext.copy(
            backgroundAlpha = rawNext.backgroundAlpha.coerceIn(0.45f, 0.98f),
            fontScale = rawNext.fontScale.coerceIn(0.85f, 1.60f),
            rideMarkerIntervalMinutes = rawNext.rideMarkerIntervalMinutes.coerceIn(1, 60)
        )
        _overlayConfig.value = next
        MotionSensorManager.setPeakHoldSeconds(next.gForcePeakHoldSeconds)
        persistOverlayConfig(context, next)
        return true
    }

    fun resetOverlayConfig(context: Context) {
        ensurePrefsLoaded(context)
        val defaults = OverlayDisplayConfig()
        _overlayConfig.value = defaults
        MotionSensorManager.setPeakHoldSeconds(defaults.gForcePeakHoldSeconds)
        persistOverlayConfig(context, defaults)
    }

    fun updateSafetyAlertConfig(
        context: Context,
        transform: (SafetyAlertConfig) -> SafetyAlertConfig
    ) {
        ensurePrefsLoaded(context)
        val next = transform(_safetyAlertConfig.value).let {
            it.copy(
                speedLimitKmh = it.speedLimitKmh.coerceIn(20, 200),
                hrLimitBpm = it.hrLimitBpm.coerceIn(110, 220)
            )
        }
        _safetyAlertConfig.value = next
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERT_SPEED_EN, next.speedAlertEnabled)
            .putInt(KEY_ALERT_SPEED_KMH, next.speedLimitKmh)
            .putBoolean(KEY_ALERT_HR_EN, next.hrAlertEnabled)
            .putInt(KEY_ALERT_HR_BPM, next.hrLimitBpm)
            .putBoolean(KEY_ALERT_VIBRATE, next.vibrateOnAlert)
            .apply()
    }

    fun saveOverlayPosition(
        context: Context,
        x: Int,
        y: Int,
        isLandscape: Boolean = isContextLandscape(context)
    ) {
        val keyX = if (isLandscape) KEY_OV_POS_X_LAND else KEY_OV_POS_X
        val keyY = if (isLandscape) KEY_OV_POS_Y_LAND else KEY_OV_POS_Y
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(keyX, x)
            .putInt(keyY, y)
            .apply()
    }

    fun loadOverlayPosition(
        context: Context,
        isLandscape: Boolean = isContextLandscape(context)
    ): Pair<Int, Int>? {
        val keyX = if (isLandscape) KEY_OV_POS_X_LAND else KEY_OV_POS_X
        val keyY = if (isLandscape) KEY_OV_POS_Y_LAND else KEY_OV_POS_Y
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(keyX) || !prefs.contains(keyY)) return null
        return prefs.getInt(keyX, 0) to prefs.getInt(keyY, 0)
    }

    private fun isContextLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun persistOverlayConfig(context: Context, cfg: OverlayDisplayConfig) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OV_SHOW_SPEED, cfg.showSpeed)
            .putBoolean(KEY_OV_SHOW_GFORCE, cfg.showGForce)
            .putBoolean(KEY_OV_SHOW_ALTITUDE, cfg.showAltitude)
            .putBoolean(KEY_OV_SHOW_HR, cfg.showHeartRate)
            .putBoolean(KEY_OV_SHOW_BEARING, cfg.showBearing)
            .putBoolean(KEY_OV_SHOW_ACCURACY, cfg.showAccuracy)
            .putBoolean(KEY_OV_SHOW_COORDS, cfg.showCoordinates)
            .putBoolean(KEY_OV_SHOW_RIDE_TIMER, cfg.showRideTimer)
            .putInt(KEY_OV_RIDE_MARKER_MINUTES, cfg.rideMarkerIntervalMinutes)
            .putBoolean(KEY_OV_SHOW_RIDE_ELAPSED, cfg.showRideElapsedTime)
            .putFloat(KEY_OV_BG_ALPHA, cfg.backgroundAlpha)
            .putString(KEY_OV_SCALE, cfg.scale.name)
            .putFloat(KEY_OV_FONT_SCALE, cfg.fontScale)
            .putBoolean(KEY_OV_SHOW_PEAK_ON_DIAL, cfg.showGForcePeakOnDial)
            .putInt(KEY_OV_PEAK_HOLD_SEC, cfg.gForcePeakHoldSeconds)
            .apply()
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
    }

    fun resetAltitudeSession() {
        synchronized(lock) {
            altitudeTracker.reset()
            val gps = _gpsData.value
            val curAlt = gps.displayAltitudeMeters.takeIf { gps.acceptedAltitudeMeters != null }
            _altitudeTelemetry.value = if (curAlt != null) {
                altitudeTracker.onAltitudeSample(
                    altitudeM = curAlt,
                    verticalAccuracyM = gps.verticalAccuracyMeters,
                    horizontalSpeedMps = gps.speedMps,
                    elapsedMs = SystemClock.elapsedRealtime()
                )
            } else {
                AltitudeTelemetry()
            }
        }
    }

    fun resetAccuracySession() {
        synchronized(lock) {
            accuracyTracker.reset()
            val curAcc = _gpsData.value.accuracyMeters
            val prev = _accuracyTelemetry.value
            _accuracyTelemetry.value = if (curAcc != null && _gpsData.value.hasFix) {
                accuracyTracker.onAccuracySample(
                    horizontalAccuracyM = curAcc,
                    verticalAccuracyM = prev.verticalAccuracyM,
                    speedAccuracyMps = prev.speedAccuracyMps,
                    bearingAccuracyDeg = prev.bearingAccuracyDeg,
                    elapsedMs = SystemClock.elapsedRealtime()
                )
            } else {
                AccuracyTelemetry()
            }
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startTracking(context: Context, clientId: String) {
        ensurePrefsLoaded(context)
        synchronized(lock) {
            val appCtx = context.applicationContext
            appContext = appCtx
            activeClients.add(clientId)

            if (!hasLocationPermission(appCtx)) {
                _gpsData.update { it.copy(fixStatus = GpsFixStatus.NO_PERMISSION) }
                return
            }

            if (!isListening) {
                registerListenersLocked(appCtx)
            } else {
                refreshProviderStatusLocked()
            }
        }
    }

    fun stopTracking(clientId: String) {
        synchronized(lock) {
            if (!activeClients.remove(clientId)) return
            if (activeClients.isEmpty()) {
                unregisterListenersLocked()
            }
        }
    }

    /** Called only by the running location foreground service. */
    internal fun recoverForegroundTracking(context: Context, clientId: String) {
        synchronized(lock) {
            if (clientId !in activeClients) return
            expireStaleFixLocked()
            if (!hasLocationPermission(context)) {
                if (isListening) unregisterListenersLocked()
                _gpsData.update { it.copy(fixStatus = GpsFixStatus.NO_PERMISSION) }
                return
            }
            val lm = locationManager ?: context.getSystemService(LocationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !lm.isLocationEnabled) return
            val now = SystemClock.elapsedRealtime()
            val lastFixOrRequest = maxOf(subscriptionStartedMs, _gpsData.value.lastUpdateElapsedRealtimeMs)
            if (now - lastFixOrRequest < RECONNECT_GAP_MS) return
            if (lastReconnectMs?.let { now - it < RECONNECT_INTERVAL_MS } == true) return

            lastReconnectMs = now
            Log.w(TAG, "Foreground location has no fresh fixes; renewing provider subscriptions")
            removeSubscriptionsLocked(lm)
            isListening = false
            registerListenersLocked(context)
        }
    }

    fun refreshTrackingState(context: Context) {
        ensurePrefsLoaded(context)
        synchronized(lock) {
            val appCtx = context.applicationContext
            appContext = appCtx
            if (!hasLocationPermission(appCtx)) {
                if (isListening) {
                    unregisterListenersLocked()
                }
                _gpsData.update { it.copy(fixStatus = GpsFixStatus.NO_PERMISSION) }
                return
            }

            if (activeClients.isNotEmpty() && !isListening) {
                registerListenersLocked(appCtx)
            } else if (isListening) {
                refreshProviderStatusLocked()
            }
        }
    }

    private fun refreshProviderStatusLocked() {
        expireStaleFixLocked()
        val status = statusWithoutFixLocked()
        if (status == GpsFixStatus.DISABLED) {
            handleGpsDisabledLocked()
        } else if (!_gpsData.value.hasFix) {
            _gpsData.update { it.copy(fixStatus = status) }
        }
    }

    private fun expireStaleFixLocked() {
        publishPendingFusedFixLocked(SystemClock.elapsedRealtime())
        val gps = _gpsData.value
        val ageMs = SystemClock.elapsedRealtime() - gps.lastUpdateElapsedRealtimeMs
        if (gps.hasFix && ageMs >= GPS_FRESHNESS_WINDOW_MS) {
            Log.w(TAG, "No fresh location for ${ageMs}ms; active clients: $activeClients")
            clearLocationFixLocked(statusWithoutFixLocked())
        }
    }

    private fun statusWithoutFixLocked(): GpsFixStatus {
        val gpsEnabled = runCatching { locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrNull() == true
        return if (gpsEnabled) GpsFixStatus.SEARCHING else GpsFixStatus.DISABLED
    }

    private fun handleGpsDisabledLocked() {
        lastGpsFixElapsedMs = 0L
        publishPendingFusedFixLocked(SystemClock.elapsedRealtime())
        if (_gpsData.value.fixStatus != GpsFixStatus.FUSED_FALLBACK) {
            clearLocationFixLocked(GpsFixStatus.DISABLED)
        }
    }

    private fun clearLocationFixLocked(status: GpsFixStatus, resetTimestamp: Boolean = false) {
        mainHandler.removeCallbacks(expireLocationFix)
        liveAltitudeFilter.reset()
        _gpsData.update { current ->
            current.copy(
                hasFix = false,
                latitude = null,
                longitude = null,
                speedMps = null,
                altitudeMeters = null,
                acceptedAltitudeMeters = null,
                smoothedAltitudeMeters = null,
                verticalAccuracyMeters = null,
                accuracyMeters = null,
                bearingDegrees = null,
                provider = null,
                fixStatus = status,
                lastUpdateElapsedRealtimeMs = if (resetTimestamp) 0L else current.lastUpdateElapsedRealtimeMs
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerListenersLocked(context: Context) {
        subscriptionStartedMs = SystemClock.elapsedRealtime()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            _gpsData.update { it.copy(fixStatus = GpsFixStatus.DISABLED) }
            return
        }
        locationManager = lm

        val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)

        _gpsData.update { current ->
            current.copy(
                fixStatus = when {
                    current.hasFix && current.provider == LocationManager.GPS_PROVIDER -> GpsFixStatus.LOCKED
                    !gpsEnabled -> GpsFixStatus.DISABLED
                    else -> GpsFixStatus.SEARCHING
                }
            )
        }

        fun subscribe(provider: String, interval: Long, listener: LocationListener): Boolean {
            if (provider !in lm.allProviders) return false
            return runCatching {
                lm.requestLocationUpdates(provider, interval, MIN_DISTANCE_M, listener, Looper.getMainLooper())
            }.onFailure { Log.w(TAG, "Failed to subscribe to $provider location", it) }.isSuccess
        }
        val native = hasFineLocationPermission(context) &&
            subscribe(LocationManager.GPS_PROVIDER, MIN_TIME_MS, gpsLocationListener)
        val fallbackProvider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            LocationManager.FUSED_PROVIDER in lm.allProviders
        ) LocationManager.FUSED_PROVIDER else LocationManager.NETWORK_PROVIDER
        val fallback = subscribe(fallbackProvider, 1_000L, fusedFallbackListener)
        isListening = native || fallback
        if (native) registerGnssCallbackLocked(context, lm)
        Log.i(TAG, "Location subscriptions: native=$native fallback=$fallback; clients=$activeClients")
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssCallbackLocked(context: Context, lm: LocationManager) {
        if (!hasFineLocationPermission(context)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssStatusCallback(context.mainExecutor, gnssStatusCallback)
            } else {
                @Suppress("DEPRECATION")
                lm.registerGnssStatusCallback(gnssStatusCallback, mainHandler)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing fine location permission for GnssStatus", se)
        } catch (e: Exception) {
            Log.w(TAG, "GnssStatus registration unavailable on this device", e)
        }
    }

    private fun unregisterListenersLocked() {
        Log.i(TAG, "Location listeners removed; active clients: $activeClients")
        locationManager?.let(::removeSubscriptionsLocked)
        isListening = false
        lastGpsFixElapsedMs = 0L
        pendingFusedFix = null
        mainHandler.removeCallbacks(promoteFusedFix)
        lastReconnectMs = null
        clearLocationFixLocked(statusWithoutFixLocked(), resetTimestamp = true)
    }

    private fun removeSubscriptionsLocked(lm: LocationManager) {
        runCatching { lm.removeUpdates(gpsLocationListener) }
        runCatching { lm.removeUpdates(fusedFallbackListener) }
        runCatching { lm.unregisterGnssStatusCallback(gnssStatusCallback) }
    }

    private val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocationReceived(location, isGpsPrimary = true)
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _gpsData.update { current ->
                    if (!current.hasFix) current.copy(fixStatus = GpsFixStatus.SEARCHING) else current
                }
            }
        }

        override fun onProviderDisabled(provider: String) {
            synchronized(lock) {
                if (isListening && provider == LocationManager.GPS_PROVIDER) handleGpsDisabledLocked()
            }
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val fusedFallbackListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocationReceived(location, isGpsPrimary = false)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private fun onLocationReceived(location: Location, isGpsPrimary: Boolean) {
        synchronized(lock) {
            if (!isListening) return

            // A delayed callback does not make its measurement fresh again.
            val fixElapsedMs = TimeUnit.NANOSECONDS.toMillis(location.elapsedRealtimeNanos)
            val now = SystemClock.elapsedRealtime()
            val ageMs = now - fixElapsedMs
            if (fixElapsedMs <= _gpsData.value.lastUpdateElapsedRealtimeMs ||
                ageMs < 0L || ageMs >= GPS_FRESHNESS_WINDOW_MS
            ) return

            val graceRemaining = lastGpsFixElapsedMs + GPS_PROVIDER_GRACE_MS - now
            if (!isGpsPrimary && lastGpsFixElapsedMs > 0L && graceRemaining > 0L) {
                if (location.elapsedRealtimeNanos > (pendingFusedFix?.elapsedRealtimeNanos ?: 0L)) {
                    pendingFusedFix = Location(location)
                    mainHandler.removeCallbacks(promoteFusedFix)
                    mainHandler.postDelayed(promoteFusedFix, graceRemaining)
                }
                return
            }
            if (isGpsPrimary) lastGpsFixElapsedMs = fixElapsedMs
            if (pendingFusedFix?.let { it.elapsedRealtimeNanos <= location.elapsedRealtimeNanos } == true) {
                pendingFusedFix = null
            }
            publishFixLocked(location, isGpsPrimary, fixElapsedMs, ageMs)
        }
    }

    private fun publishPendingFusedFixLocked(now: Long) {
        val fallback = pendingFusedFix ?: return
        val graceRemaining = lastGpsFixElapsedMs + GPS_PROVIDER_GRACE_MS - now
        if (lastGpsFixElapsedMs > 0L && graceRemaining > 0L) {
            mainHandler.removeCallbacks(promoteFusedFix)
            mainHandler.postDelayed(promoteFusedFix, graceRemaining)
            return
        }
        pendingFusedFix = null
        val elapsed = TimeUnit.NANOSECONDS.toMillis(fallback.elapsedRealtimeNanos)
        val age = now - elapsed
        if (elapsed > _gpsData.value.lastUpdateElapsedRealtimeMs && age in 0 until GPS_FRESHNESS_WINDOW_MS) {
            publishFixLocked(fallback, false, elapsed, age)
        }
    }

    private fun publishFixLocked(location: Location, isGpsPrimary: Boolean, elapsed: Long, age: Long) {
        applyLocationLocked(location, isGpsPrimary, elapsed)
        mainHandler.removeCallbacks(expireLocationFix)
        mainHandler.postDelayed(expireLocationFix, GPS_FRESHNESS_WINDOW_MS - age)
    }

    private fun applyLocationLocked(location: Location, isGpsPrimary: Boolean, fixElapsedMs: Long) {
        val spdAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond
        } else {
            null
        }
        val newSpeed = usableGpsSpeed(if (location.hasSpeed()) location.speed else null, spdAcc)
        val newAltitude = if (location.hasAltitude()) location.altitude.takeIf { it.isFinite() } else null
        val vAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters
        } else {
            null
        }
        val altitude = liveAltitudeFilter.update(newAltitude, vAcc, isGpsPrimary, fixElapsedMs)
        _gpsData.update { current ->
            val newAccuracy = if (location.hasAccuracy()) location.accuracy else null
            val newBearing = when {
                location.hasBearing() && (location.bearing != 0f || (newSpeed ?: 0f) > 0.5f) -> location.bearing
                location.hasBearing() && current.bearingDegrees == null -> location.bearing
                else -> current.bearingDegrees
            }

            current.copy(
                hasFix = true,
                latitude = location.latitude,
                longitude = location.longitude,
                speedMps = newSpeed,
                altitudeMeters = newAltitude,
                acceptedAltitudeMeters = altitude.acceptedMeters,
                smoothedAltitudeMeters = altitude.displayMeters,
                verticalAccuracyMeters = vAcc,
                accuracyMeters = newAccuracy,
                bearingDegrees = newBearing,
                provider = if (isGpsPrimary) LocationManager.GPS_PROVIDER else "fused",
                fixStatus = if (isGpsPrimary) GpsFixStatus.LOCKED else GpsFixStatus.FUSED_FALLBACK,
                lastUpdateElapsedRealtimeMs = fixElapsedMs
            )
        }

        val brgAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasBearingAccuracy()) {
            location.bearingAccuracyDegrees
        } else {
            null
        }

        if (location.hasAccuracy()) {
            _accuracyTelemetry.value = accuracyTracker.onAccuracySample(
                horizontalAccuracyM = location.accuracy,
                verticalAccuracyM = vAcc,
                speedAccuracyMps = spdAcc,
                bearingAccuracyDeg = brgAcc,
                elapsedMs = fixElapsedMs
            )
        }

        if (altitude.acceptedMeters != null && altitude.displayMeters != null) {
            if (altitude.startsNewInterval) altitudeTracker.breakInterval()
            _altitudeTelemetry.value = altitudeTracker.onAltitudeSample(
                altitudeM = altitude.displayMeters,
                verticalAccuracyM = vAcc,
                horizontalSpeedMps = newSpeed,
                elapsedMs = fixElapsedMs
            )
        }
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val totalVisible = status.satelliteCount
            var usedCount = 0
            val satList = ArrayList<GnssSatelliteInfo>(totalVisible)
            for (i in 0 until totalVisible) {
                val used = status.usedInFix(i)
                if (used) usedCount++
                satList.add(
                    GnssSatelliteInfo(
                        svid = status.getSvid(i),
                        constellation = GnssConstellation.fromGnssStatusType(status.getConstellationType(i)),
                        cn0DbHz = status.getCn0DbHz(i),
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        usedInFix = used,
                        hasAlmanac = status.hasAlmanacData(i),
                        hasEphemeris = status.hasEphemerisData(i)
                    )
                )
            }
            satList.sortWith(
                compareByDescending<GnssSatelliteInfo> { it.usedInFix }
                    .thenByDescending { it.cn0DbHz }
                    .thenBy { it.constellation.ordinal }
                    .thenBy { it.svid }
            )
            _satellites.value = satList
            _gpsData.update { current ->
                current.copy(
                    satellitesUsed = usedCount,
                    satellitesVisible = totalVisible,
                    hasSatelliteInfo = true
                )
            }
        }

        override fun onStopped() {
            _satellites.value = emptyList()
            _gpsData.update { current ->
                current.copy(
                    satellitesUsed = 0,
                    satellitesVisible = 0
                )
            }
        }
    }
}
