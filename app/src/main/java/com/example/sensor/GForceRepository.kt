package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.hypot

data class GForceSample(
    val lateralG: Float,
    val longitudinalG: Float
)

data class GForceData(
    val lateralG: Float = 0f,
    val longitudinalG: Float = 0f,
    val totalG: Float = 0f,
    val peakG: Float = 0f,
    val trail: List<GForceSample> = emptyList(),
    val isAvailable: Boolean = true
)

/**
 * Real hardware G-Force sensor stream backed by Android native SensorManager
 * (TYPE_LINEAR_ACCELERATION with TYPE_ACCELEROMETER gravity-filter fallback).
 */
object GForceRepository : SensorEventListener {
    private const val GRAVITY = SensorManager.STANDARD_GRAVITY
    private const val SMOOTHING_ALPHA = 0.25f
    private const val GRAVITY_ALPHA = 0.85f
    private const val PEAK_HOLD_MS = 4_000L
    private const val MAX_TRAIL_POINTS = 12
    private const val EMIT_INTERVAL_MS = 33L // ~30 fps for smooth, low-power HUD rendering

    private val lock = Any()
    private val activeClients = mutableSetOf<String>()

    private var sensorManager: SensorManager? = null
    private var linearAccelSensor: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var rawAccelSensor: Sensor? = null
    private var isRegistered = false

    private val gravityVec = floatArrayOf(0f, 0f, GRAVITY)
    private var filteredLatG = 0f
    private var filteredLongG = 0f
    private var peakGValue = 0f
    private var peakTimestampMs = 0L
    private var lastEmitElapsedMs = 0L
    private val trailBuffer = ArrayDeque<GForceSample>()

    private val _gForceData = MutableStateFlow(GForceData())
    val gForceData: StateFlow<GForceData> = _gForceData.asStateFlow()

    fun startListening(context: Context, clientId: String) {
        synchronized(lock) {
            activeClients.add(clientId)
            if (isRegistered) return

            val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            if (sm == null) {
                _gForceData.value = GForceData(isAvailable = false)
                return
            }
            sensorManager = sm
            linearAccelSensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            rawAccelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            if (linearAccelSensor != null) {
                sm.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME)
                gravitySensor?.let {
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                }
                isRegistered = true
            } else if (rawAccelSensor != null) {
                sm.registerListener(this, rawAccelSensor, SensorManager.SENSOR_DELAY_GAME)
                isRegistered = true
            } else {
                _gForceData.value = GForceData(isAvailable = false)
            }
        }
    }

    fun stopListening(clientId: String) {
        synchronized(lock) {
            activeClients.remove(clientId)
            if (activeClients.isEmpty() && isRegistered) {
                runCatching { sensorManager?.unregisterListener(this) }
                isRegistered = false
                trailBuffer.clear()
                filteredLatG = 0f
                filteredLongG = 0f
                peakGValue = 0f
                _gForceData.value = GForceData()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravityVec[0] = event.values[0]
                gravityVec[1] = event.values[1]
                gravityVec[2] = event.values[2]
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                processLinearAcceleration(
                    ax = event.values[0],
                    ay = event.values[1],
                    az = event.values[2]
                )
            }

            Sensor.TYPE_ACCELEROMETER -> {
                gravityVec[0] = GRAVITY_ALPHA * gravityVec[0] + (1f - GRAVITY_ALPHA) * event.values[0]
                gravityVec[1] = GRAVITY_ALPHA * gravityVec[1] + (1f - GRAVITY_ALPHA) * event.values[1]
                gravityVec[2] = GRAVITY_ALPHA * gravityVec[2] + (1f - GRAVITY_ALPHA) * event.values[2]
                processLinearAcceleration(
                    ax = event.values[0] - gravityVec[0],
                    ay = event.values[1] - gravityVec[1],
                    az = event.values[2] - gravityVec[2]
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processLinearAcceleration(ax: Float, ay: Float, az: Float) {
        // Determine horizontal forward/back axis based on whether device is mounted upright or flat
        val isUpright = abs(gravityVec[1]) > abs(gravityVec[2])
        val rawLatG = (ax / GRAVITY).coerceIn(-3f, 3f)
        val rawLongG = ((if (isUpright) -az else ay) / GRAVITY).coerceIn(-3f, 3f)

        filteredLatG += SMOOTHING_ALPHA * (rawLatG - filteredLatG)
        filteredLongG += SMOOTHING_ALPHA * (rawLongG - filteredLongG)

        // Tiny deadband to keep the dot steady when completely stationary
        val cleanLat = if (abs(filteredLatG) < 0.015f) 0f else filteredLatG
        val cleanLong = if (abs(filteredLongG) < 0.015f) 0f else filteredLongG
        val total = hypot(cleanLat, cleanLong)

        val now = SystemClock.elapsedRealtime()
        if (total >= peakGValue || (now - peakTimestampMs) > PEAK_HOLD_MS) {
            peakGValue = total
            peakTimestampMs = now
        }

        if (now - lastEmitElapsedMs < EMIT_INTERVAL_MS) return
        lastEmitElapsedMs = now

        trailBuffer.addLast(GForceSample(cleanLat, cleanLong))
        if (trailBuffer.size > MAX_TRAIL_POINTS) {
            trailBuffer.removeFirst()
        }

        _gForceData.value = GForceData(
            lateralG = cleanLat,
            longitudinalG = cleanLong,
            totalG = total,
            peakG = peakGValue,
            trail = trailBuffer.toList(),
            isAvailable = true
        )
    }
}
