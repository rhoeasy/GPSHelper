package com.example.alert

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.SafetyAlertConfig

data class SafetyAlertState(
    val isOverspeed: Boolean = false,
    val isHighHeartRate: Boolean = false
) {
    val isAnyAlert: Boolean
        get() = isOverspeed || isHighHeartRate
}

/**
 * Evaluates live overspeed and high-heart-rate safety thresholds and triggers gentle,
 * cooldown-throttled haptic pulses when enabled.
 */
object SafetyAlertMonitor {
    private const val VIBRATION_COOLDOWN_MS = 5_000L

    @Volatile
    private var lastVibrationElapsedMs = 0L

    fun evaluate(
        gpsData: GpsData,
        heartRateState: HeartRateState,
        config: SafetyAlertConfig
    ): SafetyAlertState {
        val speedKmh = if (gpsData.hasFix) (gpsData.speedMps ?: 0f) * 3.6f else 0f
        val overspeed = config.speedAlertEnabled &&
            gpsData.hasFix &&
            speedKmh >= config.speedLimitKmh.toFloat()

        val bpm = heartRateState.bpm ?: 0
        val highHr = config.hrAlertEnabled &&
            heartRateState.isConnected &&
            bpm >= config.hrLimitBpm

        return SafetyAlertState(
            isOverspeed = overspeed,
            isHighHeartRate = highHr
        )
    }

    fun maybeVibrate(context: Context, alertState: SafetyAlertState, config: SafetyAlertConfig) {
        if (!alertState.isAnyAlert || !config.vibrateOnAlert) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastVibrationElapsedMs < VIBRATION_COOLDOWN_MS) return
        lastVibrationElapsedMs = now
        vibratePulse(context.applicationContext)
    }

    private fun vibratePulse(context: Context) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Crisp double-tap haptic pulse: wait 0ms, buzz 90ms, pause 70ms, buzz 90ms
                val effect = VibrationEffect.createWaveform(longArrayOf(0L, 90L, 70L, 90L), -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0L, 90L, 70L, 90L), -1)
            }
        }
    }
}
