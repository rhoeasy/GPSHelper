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
    val degrees: Float? = null
)

internal class MotorcycleLeanMeasure {
    private var zeroRoll: Float? = null
    private var smoothed: Float? = null
    private var sampledAtMs: Long = Long.MIN_VALUE

    fun update(upX: Float, upZ: Float, timestampMs: Long) {
        if (timestampMs <= sampledAtMs) return
        val previous = freshRoll(timestampMs)
        sampledAtMs = timestampMs
        // Device +Y must face forward. Project world up onto the plane across the bike.
        if (!upX.isFinite() || !upZ.isFinite() || hypot(upX, upZ) < 0.35f) {
            smoothed = null
            return
        }
        val roll = Math.toDegrees(atan2(-upX.toDouble(), upZ.toDouble())).toFloat()
        smoothed = if (previous == null) roll else previous + 0.22f * CompassMath.shortestAngleDelta(previous, roll)
    }

    fun zero(nowMs: Long) {
        freshRoll(nowMs)?.let { zeroRoll = it }
    }

    fun read(available: Boolean, nowMs: Long): MotorcycleLeanReadout {
        if (!available) return MotorcycleLeanReadout()
        val roll = freshRoll(nowMs)
        val degrees = if (roll != null) zeroRoll?.let { CompassMath.shortestAngleDelta(it, roll) } else null
        return MotorcycleLeanReadout(true, roll != null, degrees)
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
        val rotationSensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: return@synchronized
        if (manager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)) {
            sensors = manager
            registeredSensor = rotationSensor
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
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            measure.update(rotation[6], rotation[8], event.timestamp / 1_000_000L)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
