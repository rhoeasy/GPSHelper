package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.atan2
import kotlin.math.hypot

data class MotorcycleLeanReadout(
    val sensorAvailable: Boolean = false,
    val canZero: Boolean = false,
    val degrees: Float? = null,
    val calibrated: Boolean = false
)

internal class MotorcycleLeanMeasure {
    private var zeroRoll: Float? = null
    private var smoothed: Float? = null
    private var sampledAtMs: Long = Long.MIN_VALUE

    fun update(upX: Float, upY: Float, upZ: Float, timestampMs: Long) {
        if (timestampMs <= sampledAtMs) return
        val previous = freshRoll(timestampMs)
        sampledAtMs = timestampMs
        // Device X runs across the bike; Y and Z can both carry gravity on an inclined mount.
        val upright = hypot(upY, upZ)
        if (!upX.isFinite() || !upright.isFinite() || hypot(upX, upright) < 0.35f) {
            smoothed = null
            return
        }
        val roll = Math.toDegrees(atan2(-upX.toDouble(), upright.toDouble())).toFloat()
        smoothed = if (previous == null) roll else previous + 0.22f * CompassMath.shortestAngleDelta(previous, roll)
    }

    fun zero(nowMs: Long) {
        freshRoll(nowMs)?.let { zeroRoll = it }
    }

    fun read(available: Boolean, nowMs: Long): MotorcycleLeanReadout {
        if (!available) return MotorcycleLeanReadout()
        val roll = freshRoll(nowMs)
        val degrees = roll?.let { CompassMath.shortestAngleDelta(zeroRoll ?: 0f, it) }
        return MotorcycleLeanReadout(true, roll != null, degrees, zeroRoll != null)
    }

    private fun freshRoll(nowMs: Long): Float? =
        smoothed?.takeIf { nowMs - sampledAtMs in 0L..500L }
}

internal object MotorcycleLeanSensor : SensorEventListener {
    private val lock = Any()
    private var sensors: SensorManager? = null
    private var registeredSensor: Sensor? = null
    private var measure = MotorcycleLeanMeasure()
    private val rotation = FloatArray(9)

    fun start(context: Context) = synchronized(lock) {
        stop()
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return@synchronized
        for (type in intArrayOf(Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)) {
            val sensor = manager.getDefaultSensor(type) ?: continue
            if (manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                sensors = manager
                registeredSensor = sensor
                break
            }
        }
    }

    fun stop() = synchronized(lock) {
        sensors?.unregisterListener(this)
        sensors = null
        registeredSensor = null
        measure = MotorcycleLeanMeasure()
    }

    fun zero() = synchronized(lock) { measure.zero(SystemClock.elapsedRealtime()) }

    fun current(): MotorcycleLeanReadout = synchronized(lock) {
        measure.read(registeredSensor != null, SystemClock.elapsedRealtime())
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(lock) {
            if (event.sensor != registeredSensor || event.values.size < 3) return
            val timestampMs = event.timestamp / 1_000_000L
            when (event.sensor.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    measure.update(rotation[6], rotation[7], rotation[8], timestampMs)
                }
                else -> {
                    val (x, y, z) = event.values
                    val magnitude = hypot(x, hypot(y, z))
                    if (magnitude in 7f..12f) measure.update(x / magnitude, y / magnitude, z / magnitude, timestampMs)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
