package com.example.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.pow
import kotlin.math.sqrt

data class EnvironmentTelemetry(
    val magneticAzimuthDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val magneticFieldUt: Float? = null,
    val compassAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE,
    val hasCompass: Boolean = false,
    val hasCompassReading: Boolean = false,
    val pressureHpa: Float? = null,
    val seaLevelPressureHpa: Float = SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
    val barometricAltitudeM: Float? = null,
    val hasBarometer: Boolean = false,
    val pressureSampleElapsedMs: Long = 0L
) {
    fun freshPressureHpa(nowElapsedMs: Long): Float? =
        pressureHpa?.takeIf { nowElapsedMs - pressureSampleElapsedMs in 0L..5_000L }

    fun headingDegrees(useTrueNorth: Boolean, declinationDeg: Float?): Float {
        val raw = if (useTrueNorth && declinationDeg != null) {
            magneticAzimuthDeg + declinationDeg
        } else {
            magneticAzimuthDeg
        }
        return CompassMath.normalizeDegrees(raw)
    }
}

/**
 * Pure deterministic math helpers for magnetic compass smoothing, declination, and barometric altitude.
 */
object CompassMath {
    const val STANDARD_SEA_LEVEL_HPA = SensorManager.PRESSURE_STANDARD_ATMOSPHERE // 1013.25 hPa

    fun normalizeDegrees(deg: Float): Float {
        var v = deg % 360f
        if (v < 0f) v += 360f
        return v
    }

    /**
     * Shortest signed angular delta from [fromDeg] to [toDeg] in [-180, +180].
     */
    fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
        var diff = (toDeg - fromDeg + 540f) % 360f - 180f
        if (diff < -180f) diff += 360f
        return diff
    }

    fun smoothAzimuth(currentDeg: Float, targetDeg: Float, alpha: Float): Float {
        val delta = shortestAngleDelta(currentDeg, targetDeg)
        return normalizeDegrees(currentDeg + alpha * delta)
    }

    fun pressureToAltitudeMeters(seaLevelHpa: Float, pressureHpa: Float): Float {
        if (seaLevelHpa <= 0f || pressureHpa <= 0f) return 0f
        return 44330.0f * (1.0f - (pressureHpa / seaLevelHpa).toDouble().pow(1.0 / 5.255).toFloat())
    }

    /**
     * Computes equivalent sea-level QNH pressure (hPa) so barometric altitude matches a known
     * reference altitude (e.g., locked GNSS altitude).
     */
    fun calibrateSeaLevelPressureHpa(currentPressureHpa: Float, knownAltitudeMeters: Double): Float {
        if (currentPressureHpa <= 0f) return STANDARD_SEA_LEVEL_HPA
        val factor = (1.0 - knownAltitudeMeters / 44330.0).coerceIn(0.2, 1.5)
        val qnh = currentPressureHpa / factor.pow(5.255)
        return qnh.toFloat().coerceIn(850f, 1100f)
    }

    fun computeDeclinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double): Float {
        return runCatching {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters.toFloat(),
                System.currentTimeMillis()
            ).declination
        }.getOrDefault(0f)
    }

    fun compassCardinalLabelZh(degrees: Float): String {
        val d = normalizeDegrees(degrees)
        return when {
            d >= 337.5f || d < 22.5f -> "北"
            d < 67.5f -> "东北"
            d < 112.5f -> "东"
            d < 157.5f -> "东南"
            d < 202.5f -> "南"
            d < 247.5f -> "西南"
            d < 292.5f -> "西"
            else -> "西北"
        }
    }
}

/**
 * Hardware Compass (Rotation Vector / Magnetometer + Accelerometer) and optional Barometer
 * (TYPE_PRESSURE) sensor manager. Automatically remaps compass axes to the active screen rotation
 * (Portrait or Landscape). Track recording can subscribe to pressure without starting the compass.
 */
object EnvironmentSensorManager : SensorEventListener {
    private const val EMIT_INTERVAL_MS = 33L
    private const val AZIMUTH_SMOOTH_ALPHA = 0.22f
    private const val TILT_SMOOTH_ALPHA = 0.20f

    private val lock = Any()
    private val activeClients = mutableSetOf<String>()
    private val pressureClients = mutableSetOf<String>()

    private var sensorManager: SensorManager? = null
    private var pressureManager: SensorManager? = null
    private var displayManager: DisplayManager? = null
    private var isRegistered = false
    private var usingRotationVector = false
    private var currentDisplayRotation = Surface.ROTATION_0

    private val accelValues = FloatArray(3)
    private val magValues = FloatArray(3)
    private var hasAccelSample = false
    private var hasMagSample = false

    private val rawRotationMatrix = FloatArray(9)
    private val remappedRotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var smoothedAzimuth = 0f
    private var smoothedPitch = 0f
    private var smoothedRoll = 0f
    private var azimuthInitialized = false
    private var lastEmitMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                synchronized(lock) {
                    currentDisplayRotation = readRotation(displayManager)
                }
            }
        }
    }

    private val _telemetry = MutableStateFlow(EnvironmentTelemetry())
    val telemetry: StateFlow<EnvironmentTelemetry> = _telemetry.asStateFlow()

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val hpa = event.values[0]
            if (hpa !in 200f..1200f) return
            synchronized(lock) {
                if (pressureManager == null) return
                _telemetry.update { current ->
                    current.copy(
                        pressureHpa = hpa,
                        pressureSampleElapsedMs = event.timestamp / 1_000_000L,
                        barometricAltitudeM = CompassMath.pressureToAltitudeMeters(current.seaLevelPressureHpa, hpa)
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun startPressureListening(context: Context, clientId: String) {
        synchronized(lock) {
            if (!pressureClients.add(clientId) || pressureManager != null) return
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
            if (sm.registerListener(pressureListener, sensor, SensorManager.SENSOR_DELAY_NORMAL, mainHandler)) {
                pressureManager = sm
                _telemetry.update { it.copy(hasBarometer = true) }
            }
        }
    }

    fun stopPressureListening(clientId: String) {
        synchronized(lock) {
            if (!pressureClients.remove(clientId) || pressureClients.isNotEmpty()) return
            pressureManager?.unregisterListener(pressureListener)
            pressureManager = null
            _telemetry.update {
                it.copy(pressureHpa = null, pressureSampleElapsedMs = 0L, barometricAltitudeM = null, hasBarometer = false)
            }
        }
    }

    fun startListening(context: Context, clientId: String) {
        synchronized(lock) {
            val appCtx = context.applicationContext
            startPressureListening(appCtx, clientId)
            activeClients.add(clientId)
            val dm = appCtx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            currentDisplayRotation = readRotation(dm)
            if (isRegistered) return

            val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            sensorManager = sm
            displayManager = dm

            val rotVec = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            val hasCompass = rotVec != null || (mag != null && accel != null)
            usingRotationVector = rotVec != null

            if (rotVec != null) {
                sm.registerListener(this, rotVec, SensorManager.SENSOR_DELAY_UI)
            } else if (mag != null && accel != null) {
                sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            }
            // Register magnetometer for real µT field strength & interference detection
            if (mag != null) {
                sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
            }
            runCatching { dm?.registerDisplayListener(displayListener, mainHandler) }

            hasAccelSample = false
            hasMagSample = false
            azimuthInitialized = false
            lastEmitMs = 0L
            isRegistered = true

            _telemetry.update { it.copy(hasCompass = hasCompass) }
        }
    }

    fun stopListening(clientId: String) {
        synchronized(lock) {
            stopPressureListening(clientId)
            activeClients.remove(clientId)
            if (activeClients.isEmpty() && isRegistered) {
                runCatching { sensorManager?.unregisterListener(this) }
                runCatching { displayManager?.unregisterDisplayListener(displayListener) }
                isRegistered = false
                azimuthInitialized = false
            }
        }
    }

    fun calibrateBarometerToAltitude(knownAltitudeMeters: Double): Boolean {
        val currentPressure = _telemetry.value.pressureHpa ?: return false
        val newQnh = CompassMath.calibrateSeaLevelPressureHpa(currentPressure, knownAltitudeMeters)
        val recalcAlt = CompassMath.pressureToAltitudeMeters(newQnh, currentPressure)
        _telemetry.update {
            it.copy(
                seaLevelPressureHpa = newQnh,
                barometricAltitudeM = recalcAlt
            )
        }
        return true
    }

    fun resetBarometerQnh() {
        _telemetry.update { cur ->
            val qnh = CompassMath.STANDARD_SEA_LEVEL_HPA
            val alt = cur.pressureHpa?.let { p -> CompassMath.pressureToAltitudeMeters(qnh, p) }
            cur.copy(
                seaLevelPressureHpa = qnh,
                barometricAltitudeM = alt
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmitMs < EMIT_INTERVAL_MS) return
                synchronized(lock) {
                    lastEmitMs = now
                    SensorManager.getRotationMatrixFromVector(rawRotationMatrix, event.values)
                    updateOrientationFromMatrixLocked()
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                if (usingRotationVector) return
                synchronized(lock) {
                    System.arraycopy(event.values, 0, accelValues, 0, 3)
                    hasAccelSample = true
                    maybeComputeFallbackOrientationLocked()
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                val fieldUt = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )
                synchronized(lock) {
                    System.arraycopy(event.values, 0, magValues, 0, 3)
                    hasMagSample = true
                    _telemetry.update {
                        it.copy(
                            magneticFieldUt = fieldUt,
                            compassAccuracy = event.accuracy
                        )
                    }
                    if (!usingRotationVector) {
                        maybeComputeFallbackOrientationLocked()
                    }
                }
            }

        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            _telemetry.update { it.copy(compassAccuracy = accuracy) }
        }
    }

    private fun maybeComputeFallbackOrientationLocked() {
        if (!hasAccelSample || !hasMagSample) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmitMs < EMIT_INTERVAL_MS) return
        if (SensorManager.getRotationMatrix(rawRotationMatrix, null, accelValues, magValues)) {
            lastEmitMs = now
            updateOrientationFromMatrixLocked()
        }
    }

    private fun updateOrientationFromMatrixLocked() {
        val (axisX, axisY) = when (currentDisplayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rawRotationMatrix, axisX, axisY, remappedRotationMatrix)
        SensorManager.getOrientation(remappedRotationMatrix, orientationAngles)

        val rawAzimuth = CompassMath.normalizeDegrees(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
        val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        if (!azimuthInitialized) {
            smoothedAzimuth = rawAzimuth
            smoothedPitch = rawPitch
            smoothedRoll = rawRoll
            azimuthInitialized = true
        } else {
            smoothedAzimuth = CompassMath.smoothAzimuth(smoothedAzimuth, rawAzimuth, AZIMUTH_SMOOTH_ALPHA)
            smoothedPitch += TILT_SMOOTH_ALPHA * (rawPitch - smoothedPitch)
            smoothedRoll += TILT_SMOOTH_ALPHA * (rawRoll - smoothedRoll)
        }

        _telemetry.update { cur ->
            cur.copy(
                magneticAzimuthDeg = smoothedAzimuth,
                pitchDeg = smoothedPitch,
                rollDeg = smoothedRoll,
                hasCompass = true,
                hasCompassReading = true
            )
        }
    }

    private fun readRotation(dm: DisplayManager?): Int {
        return runCatching {
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        }.getOrDefault(Surface.ROTATION_0)
    }
}
