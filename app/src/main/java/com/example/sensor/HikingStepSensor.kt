package com.example.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

internal class HikingStepCount(initialCount: Int? = null) {
    var count: Int? = initialCount
        private set
    private var activeSinceNs: Long? = null
    private var counterBaseline: Int? = null

    fun resume(nowNs: Long) {
        activeSinceNs = nowNs
        counterBaseline = null
    }

    fun pause() {
        activeSinceNs = null
    }

    fun detectStep(timestampNs: Long) {
        val since = activeSinceNs ?: return
        if (timestampNs >= since) count = (count ?: 0) + 1
    }

    fun readCounter(total: Int, timestampNs: Long) {
        val since = activeSinceNs ?: return
        if (total < 0) return
        val previous = counterBaseline
        counterBaseline = total
        if (timestampNs < since) return
        count = (count ?: 0) + if (previous != null && total >= previous) total - previous else 0
    }
}

internal object HikingStepSensor : SensorEventListener {
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var manager: SensorManager? = null
    private var stepCount: HikingStepCount? = null

    fun hasSensor(context: Context): Boolean = sensorManager(context)?.let { findSensor(it) } != null

    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        synchronized(lock) {
            if (!hasPermission(context)) return
            val sensors = sensorManager(context) ?: return
            val sensor = findSensor(sensors) ?: return
            val registered = runCatching {
                sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, handler)
            }.getOrDefault(false)
            if (!registered) return
            manager = sensors
            val initialCount = if (sensor.type == Sensor.TYPE_STEP_DETECTOR) 0 else null
            stepCount = HikingStepCount(initialCount).apply { resume(SystemClock.elapsedRealtimeNanos()) }
        }
    }

    fun pause() = synchronized(lock) { stepCount?.pause() }

    fun resume() = synchronized(lock) { stepCount?.resume(SystemClock.elapsedRealtimeNanos()) }

    fun currentCount(): Int? = synchronized(lock) { stepCount?.count }

    fun stop(): Int? = synchronized(lock) {
        manager?.unregisterListener(this)
        manager = null
        val count = stepCount?.count
        stepCount = null
        count
    }

    override fun onSensorChanged(event: SensorEvent) {
        synchronized(lock) {
            val count = stepCount ?: return
            when (event.sensor.type) {
                Sensor.TYPE_STEP_DETECTOR -> count.detectStep(event.timestamp)
                Sensor.TYPE_STEP_COUNTER -> {
                    val total = event.values[0]
                    if (total.isFinite() && total >= 0f) count.readCounter(total.toInt(), event.timestamp)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sensorManager(context: Context) = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private fun findSensor(manager: SensorManager): Sensor? = manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
}
