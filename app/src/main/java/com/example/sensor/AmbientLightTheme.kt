package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AmbientThemePolicy {
    var isDark: Boolean? = null
        private set
    var pendingIsDark: Boolean? = null
        private set

    fun offer(lux: Float): Boolean {
        if (!lux.isFinite() || lux < 0f) return false
        val target = when {
            lux <= 15f -> true
            lux >= 40f -> false
            else -> null
        }
        val next = target?.takeUnless { it == isDark }
        if (next == pendingIsDark) return false
        pendingIsDark = next
        return true
    }

    fun settle(): Boolean? {
        pendingIsDark?.let { isDark = it }
        pendingIsDark = null
        return isDark
    }
}

/** The main window and overlay share one light sensor subscription on the main thread. */
internal object AmbientLightTheme {
    private const val LIGHT_STABILITY_MS = 5_000L
    private val handler = Handler(Looper.getMainLooper())
    private var clients = 0
    private var manager: SensorManager? = null
    private var policy = AmbientThemePolicy()
    private val _darkTheme = MutableStateFlow<Boolean?>(null)
    val darkTheme = _darkTheme.asStateFlow()

    private val settleTheme = Runnable { _darkTheme.value = policy.settle() }
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (manager == null || !policy.offer(event.values[0])) return
            handler.removeCallbacks(settleTheme)
            // Light sensors are on-change; the timer must also work without another reading.
            if (policy.pendingIsDark != null) handler.postDelayed(settleTheme, LIGHT_STABILITY_MS)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(context: Context) {
        clients++
        if (manager != null) return
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val light = sensors.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        if (sensors.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL, handler)) {
            manager = sensors
        }
    }

    fun stop() {
        clients--
        if (clients != 0) return
        manager?.unregisterListener(listener)
        manager = null
        handler.removeCallbacks(settleTheme)
        policy = AmbientThemePolicy()
        _darkTheme.value = null
    }
}
