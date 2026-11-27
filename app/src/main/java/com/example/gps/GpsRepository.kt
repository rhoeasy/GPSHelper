package com.example.gps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton GPS telemetry hub backed exclusively by native Android LocationManager
 * and GnssStatus.Callback (API 24+). Shares a single active listener across MainActivity
 * and FloatingGpsService using reference-counted client IDs.
 */
object GpsRepository {
    private const val TAG = "GpsRepository"
    private const val PREFS_NAME = "gps_hud_prefs"
    private const val KEY_SPEED_UNIT = "speed_unit"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTO_FOCUS_ON_LAUNCH = "auto_focus_on_launch"

    private const val MIN_TIME_MS = 500L
    private const val MIN_DISTANCE_M = 0f
    private const val GPS_FRESHNESS_WINDOW_MS = 8_000L

    private val lock = Any()
    private val activeClients = mutableSetOf<String>()

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var isListening = false
    private var lastGpsFixElapsedMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData: StateFlow<GpsData> = _gpsData.asStateFlow()

    private val _speedUnit = MutableStateFlow(SpeedUnit.KMH)
    val speedUnit: StateFlow<SpeedUnit> = _speedUnit.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _autoFocusOnLaunch = MutableStateFlow(false)
    val autoFocusOnLaunch: StateFlow<Boolean> = _autoFocusOnLaunch.asStateFlow()

    private val _isOverlayRunning = MutableStateFlow(false)
    val isOverlayRunning: StateFlow<Boolean> = _isOverlayRunning.asStateFlow()

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
            _keepScreenOn.value = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
            val savedTheme = prefs.getString(KEY_THEME_MODE, ThemeMode.AUTO.name)
            _themeMode.value = runCatching { ThemeMode.valueOf(savedTheme ?: ThemeMode.AUTO.name) }
                .getOrDefault(ThemeMode.AUTO)
            _autoFocusOnLaunch.value = prefs.getBoolean(KEY_AUTO_FOCUS_ON_LAUNCH, false)
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

    fun setAutoFocusOnLaunch(context: Context, enabled: Boolean) {
        ensurePrefsLoaded(context)
        _autoFocusOnLaunch.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_FOCUS_ON_LAUNCH, enabled)
            .apply()
    }

    fun setOverlayRunning(running: Boolean) {
        _isOverlayRunning.value = running
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
            activeClients.remove(clientId)
            if (activeClients.isEmpty() && isListening) {
                unregisterListenersLocked()
            }
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
        val lm = locationManager ?: return
        val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
        if (!gpsEnabled) {
            _gpsData.update { current ->
                if (current.fixStatus != GpsFixStatus.FUSED_FALLBACK) {
                    current.copy(fixStatus = GpsFixStatus.DISABLED)
                } else {
                    current
                }
            }
        } else if (!_gpsData.value.hasFix) {
            _gpsData.update { it.copy(fixStatus = GpsFixStatus.SEARCHING) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerListenersLocked(context: Context) {
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

        try {
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    gpsLocationListener,
                    Looper.getMainLooper()
                )
            }

            // Optional native FUSED_PROVIDER fallback on API 31+ (no Play Services required)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER)
            ) {
                lm.requestLocationUpdates(
                    LocationManager.FUSED_PROVIDER,
                    1_000L,
                    MIN_DISTANCE_M,
                    fusedFallbackListener,
                    Looper.getMainLooper()
                )
            }

            registerGnssCallbackLocked(context, lm)
            isListening = true
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing location permission while registering listeners", se)
            _gpsData.update { it.copy(fixStatus = GpsFixStatus.NO_PERMISSION) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register location listeners", e)
        }
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
        val lm = locationManager
        if (lm != null) {
            runCatching { lm.removeUpdates(gpsLocationListener) }
            runCatching { lm.removeUpdates(fusedFallbackListener) }
            runCatching { lm.unregisterGnssStatusCallback(gnssStatusCallback) }
        }
        isListening = false
    }

    private val gpsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = SystemClock.elapsedRealtime()
            lastGpsFixElapsedMs = now
            applyLocation(location = location, isGpsPrimary = true, nowElapsedMs = now)
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _gpsData.update { current ->
                    if (!current.hasFix) current.copy(fixStatus = GpsFixStatus.SEARCHING) else current
                }
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _gpsData.update { current ->
                    current.copy(fixStatus = GpsFixStatus.DISABLED)
                }
            }
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private val fusedFallbackListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val now = SystemClock.elapsedRealtime()
            // Only accept fallback reading if GPS has not produced a fresh fix recently
            val hasFreshGps = lastGpsFixElapsedMs > 0L &&
                (now - lastGpsFixElapsedMs) < GPS_FRESHNESS_WINDOW_MS
            if (!hasFreshGps) {
                applyLocation(location = location, isGpsPrimary = false, nowElapsedMs = now)
            }
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    private fun applyLocation(location: Location, isGpsPrimary: Boolean, nowElapsedMs: Long) {
        _gpsData.update { current ->
            val newSpeed = if (location.hasSpeed()) location.speed else current.speedMps
            val newAltitude = if (location.hasAltitude()) location.altitude else current.altitudeMeters
            val newAccuracy = if (location.hasAccuracy()) location.accuracy else current.accuracyMeters
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
                accuracyMeters = newAccuracy,
                bearingDegrees = newBearing,
                provider = if (isGpsPrimary) LocationManager.GPS_PROVIDER else "fused",
                fixStatus = if (isGpsPrimary) GpsFixStatus.LOCKED else GpsFixStatus.FUSED_FALLBACK,
                lastUpdateElapsedRealtimeMs = nowElapsedMs
            )
        }
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val totalVisible = status.satelliteCount
            var usedCount = 0
            for (i in 0 until totalVisible) {
                if (status.usedInFix(i)) {
                    usedCount++
                }
            }
            _gpsData.update { current ->
                current.copy(
                    satellitesUsed = usedCount,
                    satellitesVisible = totalVisible,
                    hasSatelliteInfo = true
                )
            }
        }

        override fun onStopped() {
            _gpsData.update { current ->
                current.copy(
                    satellitesUsed = 0,
                    satellitesVisible = 0
                )
            }
        }
    }
}
