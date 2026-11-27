package com.example.sensor

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Facade repository exposing the live Accelerometer + Gyroscope fused G-Force stream
 * from [MotionSensorManager] to the floating HUD overlay and track recorder.
 */
object GForceRepository {
    val gForceData: StateFlow<GForceData> = MotionSensorManager.gForceData

    fun startListening(context: Context, clientId: String) {
        MotionSensorManager.startListening(context, clientId)
    }

    fun stopListening(clientId: String) {
        MotionSensorManager.stopListening(clientId)
    }

    fun calibrateZero() {
        MotionSensorManager.calibrateZero()
    }

    fun resetPeak() {
        MotionSensorManager.resetPeak()
    }
}
