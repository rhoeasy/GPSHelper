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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

internal class HikingStepCount(initialCount: Int? = null) {
    private data class ActiveInterval(val startNs: Long, var endNs: Long? = null)
    private data class CounterReading(val total: Int, val timestampNs: Long)

    var count: Int? = initialCount
        private set
    val lastActiveEndNs: Long?
        get() = intervals.lastOrNull()?.endNs
    private val intervals = mutableListOf<ActiveInterval>()
    private var lastCounter: CounterReading? = null

    fun resume(nowNs: Long) {
        intervals.add(ActiveInterval(nowNs))
    }

    fun pause(nowNs: Long) {
        val interval = intervals.lastOrNull() ?: return
        if (interval.endNs == null) interval.endNs = nowNs
    }

    fun detectStep(timestampNs: Long) {
        if (intervalAt(timestampNs) != null) count = (count ?: 0) + 1
    }

    fun readCounter(total: Int, timestampNs: Long) {
        if (total < 0) return
        val previous = lastCounter
        lastCounter = CounterReading(total, timestampNs)
        val interval = intervalAt(timestampNs) ?: return
        val delta = if (previous != null && total > previous.total) {
            if (interval === intervals.first() || previous.timestampNs >= interval.startNs) {
                total - previous.total
            } else {
                // An aggregate straddling a pause only identifies when its last step happened.
                1
            }
        } else {
            0
        }
        count = (count ?: 0) + delta
    }

    private fun intervalAt(timestampNs: Long): ActiveInterval? = intervals.lastOrNull {
        timestampNs >= it.startNs && timestampNs < (it.endNs ?: Long.MAX_VALUE)
    }
}

internal object HikingStepSensor : SensorEventListener {
    private const val STEP_DETECTOR_LATENCY_MS = 2_000L
    private const val STEP_COUNTER_LATENCY_MS = 10_000L
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var manager: SensorManager? = null
    private var stepCount: HikingStepCount? = null
    private var reportLatencyMs = 0L

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
            val isDetector = sensor.type == Sensor.TYPE_STEP_DETECTOR
            reportLatencyMs = if (isDetector) STEP_DETECTOR_LATENCY_MS else STEP_COUNTER_LATENCY_MS
            val initialCount = if (isDetector) 0 else null
            stepCount = HikingStepCount(initialCount).apply { resume(SystemClock.elapsedRealtimeNanos()) }
        }
    }

    fun pause() = synchronized(lock) { stepCount?.pause(SystemClock.elapsedRealtimeNanos()) }

    fun resume() = synchronized(lock) { stepCount?.resume(SystemClock.elapsedRealtimeNanos()) }

    fun currentCount(): Int? = synchronized(lock) { stepCount?.count }

    suspend fun awaitFinalCount(): Int? {
        val remainingMs = synchronized(lock) {
            val endNs = stepCount?.lastActiveEndNs ?: return null
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - endNs)
            (reportLatencyMs - elapsedMs).coerceAtLeast(0L)
        }
        delay(remainingMs)
        return currentCount()
    }

    fun stop() {
        synchronized(lock) {
            manager?.unregisterListener(this)
            manager = null
            stepCount = null
        }
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
