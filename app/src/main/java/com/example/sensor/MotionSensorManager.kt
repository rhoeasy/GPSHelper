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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

data class GForceSample(
    val lateralG: Float,
    val longitudinalG: Float
)

data class ImuRawData(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f
)

data class GForceData(
    val lateralG: Float = 0f,
    val longitudinalG: Float = 0f,
    val verticalG: Float = 0f,
    val totalG: Float = 0f,
    val total3DG: Float = 0f,
    val peakG: Float = 0f,
    val yawRateDegPerSec: Float = 0f,
    val rawImu: ImuRawData = ImuRawData(),
    val trail: List<GForceSample> = emptyList(),
    val hasAccelerometer: Boolean = true,
    val hasGyroscope: Boolean = false,
    val isGyroFused: Boolean = false,
    val isAvailable: Boolean = true
)

/**
 * Pure, deterministic Accelerometer + Gyroscope complementary-filter engine.
 *
 * Uses rigid-body gyro kinematics (dg/dt = -ω × g) fused with accelerometer gravity tracking
 * to estimate true device tilt at any mount angle (flat, 45° handlebar, or upright), removes
 * gravity without cornering-induced lag, and projects linear acceleration onto the true
 * horizontal vehicle plane (lateral + longitudinal G).
 */
class GForceFusionEngine {
    companion object {
        const val STANDARD_GRAVITY = SensorManager.STANDARD_GRAVITY // 9.80665 m/s²
        private const val GYRO_TRUST_STEADY = 0.96f
        private const val GYRO_TRUST_DYNAMIC = 0.992f
        private const val ACCEL_ONLY_LPF_ALPHA = 0.85f
        private const val DYNAMIC_ACCEL_THRESHOLD_MPS2 = 1.2f
        private const val OUTPUT_SMOOTHING_ALPHA = 0.28f
        private const val STATIONARY_DEADBAND_G = 0.015f
        private const val PEAK_HOLD_MS = 4_000L
        private const val MAX_TRAIL_POINTS = 12
        private const val MAX_GYRO_DT_SEC = 0.1f
        private const val RAD_TO_DEG = (180.0 / PI).toFloat()
    }

    private var gravityInitialized = false
    private var gx = 0f
    private var gy = 0f
    private var gz = STANDARD_GRAVITY

    private var lastGyroTimestampNs = 0L
    private var gyroSamplesReceived = false
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    private var yawRateDegPerSec = 0f

    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = STANDARD_GRAVITY

    private var filteredLatG = 0f
    private var filteredLongG = 0f
    private var filteredVertG = 0f
    private var peakGValue = 0f
    private var peakTimestampMs = 0L

    private val trailBuffer = ArrayDeque<GForceSample>()

    fun reset() {
        gravityInitialized = false
        gx = 0f
        gy = 0f
        gz = STANDARD_GRAVITY
        lastGyroTimestampNs = 0L
        gyroSamplesReceived = false
        lastGyroX = 0f
        lastGyroY = 0f
        lastGyroZ = 0f
        yawRateDegPerSec = 0f
        filteredLatG = 0f
        filteredLongG = 0f
        filteredVertG = 0f
        peakGValue = 0f
        peakTimestampMs = 0L
        trailBuffer.clear()
    }

    /**
     * Re-zeros the reference gravity plane to the current mount orientation and clears peaks.
     */
    fun calibrateZero() {
        val mag = sqrt(lastAccelX * lastAccelX + lastAccelY * lastAccelY + lastAccelZ * lastAccelZ)
        if (mag > 1f) {
            val scale = STANDARD_GRAVITY / mag
            gx = lastAccelX * scale
            gy = lastAccelY * scale
            gz = lastAccelZ * scale
            gravityInitialized = true
        }
        filteredLatG = 0f
        filteredLongG = 0f
        filteredVertG = 0f
        peakGValue = 0f
        peakTimestampMs = 0L
        trailBuffer.clear()
    }

    fun resetPeak() {
        peakGValue = 0f
        peakTimestampMs = 0L
    }

    /**
     * Integrates gyroscope angular velocity (rad/s) to rotate the estimated gravity vector
     * in the device body frame: g_next = g - (ω × g) * dt.
     */
    fun onGyroscopeSample(wx: Float, wy: Float, wz: Float, timestampNs: Long) {
        lastGyroX = wx
        lastGyroY = wy
        lastGyroZ = wz
        gyroSamplesReceived = true

        if (lastGyroTimestampNs != 0L && gravityInitialized) {
            val dtSec = ((timestampNs - lastGyroTimestampNs) * 1e-9f).coerceIn(0f, MAX_GYRO_DT_SEC)
            if (dtSec > 0f) {
                // ω × g
                val crossX = wy * gz - wz * gy
                val crossY = wz * gx - wx * gz
                val crossZ = wx * gy - wy * gx

                gx -= crossX * dtSec
                gy -= crossY * dtSec
                gz -= crossZ * dtSec
                normalizeGravity()
            }
        }
        lastGyroTimestampNs = timestampNs

        // Yaw rate around vertical gravity axis (deg/s)
        val gMag = sqrt(gx * gx + gy * gy + gz * gz)
        if (gMag > 0.1f) {
            val ux = gx / gMag
            val uy = gy / gMag
            val uz = gz / gMag
            yawRateDegPerSec = -(wx * ux + wy * uy + wz * uz) * RAD_TO_DEG
        }
    }

    /**
     * Fuses raw accelerometer reading (m/s²) with gyro-predicted gravity vector,
     * removes gravity, projects linear acceleration onto the horizontal plane, and
     * computes instantaneous & peak G-forces.
     */
    fun onAccelerometerSample(
        ax: Float,
        ay: Float,
        az: Float,
        elapsedRealtimeMs: Long,
        hasGyroscope: Boolean,
        recordTrail: Boolean = true
    ): GForceData {
        lastAccelX = ax
        lastAccelY = ay
        lastAccelZ = az

        val accelMag = sqrt(ax * ax + ay * ay + az * az)

        if (!gravityInitialized) {
            if (accelMag > 1f) {
                val scale = STANDARD_GRAVITY / accelMag
                gx = ax * scale
                gy = ay * scale
                gz = az * scale
                gravityInitialized = true
            }
        } else if (accelMag > 1f) {
            val isDynamic = abs(accelMag - STANDARD_GRAVITY) > DYNAMIC_ACCEL_THRESHOLD_MPS2
            val alpha = when {
                gyroSamplesReceived && isDynamic -> GYRO_TRUST_DYNAMIC
                gyroSamplesReceived -> GYRO_TRUST_STEADY
                else -> ACCEL_ONLY_LPF_ALPHA
            }
            val scale = STANDARD_GRAVITY / accelMag
            val measGx = ax * scale
            val measGy = ay * scale
            val measGz = az * scale

            gx = alpha * gx + (1f - alpha) * measGx
            gy = alpha * gy + (1f - alpha) * measGy
            gz = alpha * gz + (1f - alpha) * measGz
            normalizeGravity()
        }

        // Pure linear acceleration in device coordinates (m/s²)
        val linX = ax - gx
        val linY = ay - gy
        val linZ = az - gz

        // Unit gravity vector g_hat
        val gMag = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(1e-4f)
        val ux = gx / gMag
        val uy = gy / gMag
        val uz = gz / gMag

        // Vertical component along gravity & residual horizontal vector
        val vertAcc = linX * ux + linY * uy + linZ * uz
        val horizX = linX - vertAcc * ux
        val horizY = linY - vertAcc * uy
        val horizZ = linZ - vertAcc * uz

        // Orthonormal horizontal basis:
        // r_hat = normalized projection of device +X (or +Y if device held sideways) onto horizontal plane
        val refX: Float
        val refY: Float
        val refZ: Float
        if (abs(ux) < 0.92f) {
            refX = 1f; refY = 0f; refZ = 0f
        } else {
            refX = 0f; refY = 1f; refZ = 0f
        }
        val refDotG = refX * ux + refY * uy + refZ * uz
        var rx = refX - refDotG * ux
        var ry = refY - refDotG * uy
        var rz = refZ - refDotG * uz
        val rMag = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1e-4f)
        rx /= rMag; ry /= rMag; rz /= rMag

        // f_hat = g_hat × r_hat (works continuously across flat 0° to upright 90° mount angles)
        val fx = uy * rz - uz * ry
        val fy = uz * rx - ux * rz
        val fz = ux * ry - uy * rx

        val rawLatG = ((horizX * rx + horizY * ry + horizZ * rz) / STANDARD_GRAVITY).coerceIn(-3f, 3f)
        val rawLongG = ((horizX * fx + horizY * fy + horizZ * fz) / STANDARD_GRAVITY).coerceIn(-3f, 3f)
        val rawVertG = (vertAcc / STANDARD_GRAVITY).coerceIn(-3f, 3f)

        filteredLatG += OUTPUT_SMOOTHING_ALPHA * (rawLatG - filteredLatG)
        filteredLongG += OUTPUT_SMOOTHING_ALPHA * (rawLongG - filteredLongG)
        filteredVertG += OUTPUT_SMOOTHING_ALPHA * (rawVertG - filteredVertG)

        val cleanLat = if (abs(filteredLatG) < STATIONARY_DEADBAND_G) 0f else filteredLatG
        val cleanLong = if (abs(filteredLongG) < STATIONARY_DEADBAND_G) 0f else filteredLongG
        val cleanVert = if (abs(filteredVertG) < STATIONARY_DEADBAND_G) 0f else filteredVertG

        val totalHorizG = hypot(cleanLat, cleanLong)
        val total3DG = sqrt(cleanLat * cleanLat + cleanLong * cleanLong + cleanVert * cleanVert)

        if (totalHorizG >= peakGValue || (elapsedRealtimeMs - peakTimestampMs) > PEAK_HOLD_MS) {
            peakGValue = totalHorizG
            peakTimestampMs = elapsedRealtimeMs
        }

        if (recordTrail) {
            trailBuffer.addLast(GForceSample(cleanLat, cleanLong))
            if (trailBuffer.size > MAX_TRAIL_POINTS) {
                trailBuffer.removeFirst()
            }
        }

        return GForceData(
            lateralG = cleanLat,
            longitudinalG = cleanLong,
            verticalG = cleanVert,
            totalG = totalHorizG,
            total3DG = total3DG,
            peakG = peakGValue,
            yawRateDegPerSec = yawRateDegPerSec,
            rawImu = ImuRawData(
                accelX = ax,
                accelY = ay,
                accelZ = az,
                gyroX = lastGyroX,
                gyroY = lastGyroY,
                gyroZ = lastGyroZ
            ),
            trail = trailBuffer.toList(),
            hasAccelerometer = true,
            hasGyroscope = hasGyroscope,
            isGyroFused = hasGyroscope && gyroSamplesReceived,
            isAvailable = true
        )
    }

    private fun normalizeGravity() {
        val mag = sqrt(gx * gx + gy * gy + gz * gz)
        if (mag > 1e-4f) {
            val scale = STANDARD_GRAVITY / mag
            gx *= scale
            gy *= scale
            gz *= scale
        }
    }
}

/**
 * Hardware IMU Sensor Manager reading native Android Accelerometer (TYPE_ACCELEROMETER)
 * and Gyroscope (TYPE_GYROSCOPE) in real time, computing tilt-compensated instantaneous
 * G-forces via [GForceFusionEngine] for the floating HUD overlay and track recorder.
 *
 * Uses reference-counted client IDs so multiple consumers share a single sensor registration.
 */
object MotionSensorManager : SensorEventListener {
    private const val EMIT_INTERVAL_MS = 33L // ~30 fps for smooth, low-power HUD rendering

    private val lock = Any()
    private val activeClients = mutableSetOf<String>()
    private val fusionEngine = GForceFusionEngine()

    private var androidSensorManager: SensorManager? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var isRegistered = false
    private var lastEmitElapsedMs = 0L

    private val _gForceData = MutableStateFlow(GForceData())
    val gForceData: StateFlow<GForceData> = _gForceData.asStateFlow()

    fun startListening(context: Context, clientId: String) {
        synchronized(lock) {
            activeClients.add(clientId)
            if (isRegistered) return

            val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            if (sm == null) {
                _gForceData.value = GForceData(
                    hasAccelerometer = false,
                    hasGyroscope = false,
                    isAvailable = false
                )
                return
            }

            androidSensorManager = sm
            accelerometerSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gyroscopeSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

            val accel = accelerometerSensor
            val gyro = gyroscopeSensor

            if (accel == null) {
                _gForceData.value = GForceData(
                    hasAccelerometer = false,
                    hasGyroscope = gyro != null,
                    isAvailable = false
                )
                return
            }

            fusionEngine.reset()
            lastEmitElapsedMs = 0L

            sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            if (gyro != null) {
                sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
            }
            isRegistered = true

            _gForceData.value = GForceData(
                hasAccelerometer = true,
                hasGyroscope = gyro != null,
                isGyroFused = false,
                isAvailable = true
            )
        }
    }

    fun stopListening(clientId: String) {
        synchronized(lock) {
            activeClients.remove(clientId)
            if (activeClients.isEmpty() && isRegistered) {
                runCatching { androidSensorManager?.unregisterListener(this) }
                isRegistered = false
                fusionEngine.reset()
                lastEmitElapsedMs = 0L
                _gForceData.value = GForceData()
            }
        }
    }

    fun calibrateZero() {
        val updated = synchronized(lock) {
            fusionEngine.calibrateZero()
            _gForceData.value.copy(
                lateralG = 0f,
                longitudinalG = 0f,
                verticalG = 0f,
                totalG = 0f,
                total3DG = 0f,
                peakG = 0f,
                trail = emptyList()
            )
        }
        _gForceData.value = updated
    }

    fun resetPeak() {
        val updated = synchronized(lock) {
            fusionEngine.resetPeak()
            _gForceData.value.copy(peakG = _gForceData.value.totalG)
        }
        _gForceData.value = updated
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                synchronized(lock) {
                    fusionEngine.onGyroscopeSample(
                        wx = event.values[0],
                        wy = event.values[1],
                        wz = event.values[2],
                        timestampNs = event.timestamp
                    )
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val now = SystemClock.elapsedRealtime()
                val shouldEmit = (now - lastEmitElapsedMs) >= EMIT_INTERVAL_MS
                val snapshot = synchronized(lock) {
                    if (shouldEmit) {
                        lastEmitElapsedMs = now
                    }
                    fusionEngine.onAccelerometerSample(
                        ax = event.values[0],
                        ay = event.values[1],
                        az = event.values[2],
                        elapsedRealtimeMs = now,
                        hasGyroscope = gyroscopeSensor != null,
                        recordTrail = shouldEmit
                    )
                }
                if (shouldEmit) {
                    _gForceData.value = snapshot
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
