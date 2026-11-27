package com.example.sensor

import android.content.Context
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
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
    val peakLateralG: Float = 0f,
    val peakLongitudinalG: Float = 0f,
    val peakEnvelope: List<Float> = emptyList(),
    val yawRateDegPerSec: Float = 0f,
    val rawImu: ImuRawData = ImuRawData(),
    val trail: List<GForceSample> = emptyList(),
    val hasAccelerometer: Boolean = true,
    val hasGyroscope: Boolean = false,
    val isGyroFused: Boolean = false,
    val isAvailable: Boolean = true,
    val displayRotation: Int = Surface.ROTATION_0,
    val verticalVibrationG: Float = 0f
) {
    val isLandscape: Boolean
        get() = displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270
}

internal class TrackVibrationWindow {
    private var squaredSum = 0.0
    private var count = 0

    fun add(accelerationG: Float) {
        if (!accelerationG.isFinite()) return
        squaredSum += accelerationG.toDouble() * accelerationG
        count++
    }

    fun takeRmsG(): Float? {
        val rms = if (count >= 5) sqrt(squaredSum / count).toFloat() else null
        squaredSum = 0.0
        count = 0
        return rms
    }
}

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
        const val PEAK_ENVELOPE_SECTORS = 36 // 10° per sector around 360° friction circle
        private const val ENVELOPE_MIN_G = 0.04f
        private const val GYRO_TRUST_STEADY = 0.96f
        private const val GYRO_TRUST_DYNAMIC = 0.992f
        private const val ACCEL_ONLY_LPF_ALPHA = 0.85f
        private const val DYNAMIC_ACCEL_THRESHOLD_MPS2 = 1.2f
        private const val OUTPUT_SMOOTHING_ALPHA = 0.28f
        private const val STATIONARY_DEADBAND_G = 0.015f
        private const val DEFAULT_PEAK_HOLD_MS = 60_000L
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
    private var peakLatGValue = 0f
    private var peakLongGValue = 0f
    private var peakTimestampMs = 0L
    private var peakHoldDurationMs: Long = DEFAULT_PEAK_HOLD_MS
    private var displayRotation: Int = Surface.ROTATION_0

    private val sectorPeakG = FloatArray(PEAK_ENVELOPE_SECTORS)
    private val sectorPeakTsMs = LongArray(PEAK_ENVELOPE_SECTORS)
    private val trailBuffer = ArrayDeque<GForceSample>()

    fun setPeakHoldDurationMs(durationMs: Long) {
        peakHoldDurationMs = durationMs.coerceAtLeast(0L)
    }

    /**
     * Updates the current Android display rotation (Surface.ROTATION_0 / 90 / 180 / 270) so
     * lateralG and longitudinalG always align with the rider's screen orientation (portrait
     * or left/right motorcycle landscape mount) without disrupting the gravity estimate.
     */
    fun setDisplayRotation(rotation: Int) {
        val normalized = when (rotation) {
            Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270 -> rotation
            else -> Surface.ROTATION_0
        }
        if (displayRotation != normalized) {
            displayRotation = normalized
            // Clear short trail & envelope so old-orientation vectors do not smear sideways
            filteredLatG = 0f
            filteredLongG = 0f
            sectorPeakG.fill(0f)
            sectorPeakTsMs.fill(0L)
            trailBuffer.clear()
        }
    }

    fun getDisplayRotation(): Int = displayRotation

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
        peakLatGValue = 0f
        peakLongGValue = 0f
        peakTimestampMs = 0L
        sectorPeakG.fill(0f)
        sectorPeakTsMs.fill(0L)
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
        peakLatGValue = 0f
        peakLongGValue = 0f
        peakTimestampMs = 0L
        sectorPeakG.fill(0f)
        sectorPeakTsMs.fill(0L)
        trailBuffer.clear()
    }

    fun resetPeak() {
        peakGValue = 0f
        peakLatGValue = 0f
        peakLongGValue = 0f
        peakTimestampMs = 0L
        sectorPeakG.fill(0f)
        sectorPeakTsMs.fill(0L)
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

        // Orthonormal horizontal basis aligned to the active display orientation:
        // Screen-Right vector in raw device coordinates for ROTATION_0 / 90 / 180 / 270:
        //   ROTATION_0   (portrait):         screenRight = (+1,  0, 0), screenUp = ( 0, +1, 0)
        //   ROTATION_90  (landscape CCW):    screenRight = ( 0, -1, 0), screenUp = (+1,  0, 0)
        //   ROTATION_180 (reverse portrait): screenRight = (-1,  0, 0), screenUp = ( 0, -1, 0)
        //   ROTATION_270 (landscape CW):     screenRight = ( 0, +1, 0), screenUp = (-1,  0, 0)
        val rightRefX: Float
        val rightRefY: Float
        val upRefX: Float
        val upRefY: Float
        when (displayRotation) {
            Surface.ROTATION_90 -> {
                rightRefX = 0f; rightRefY = -1f
                upRefX = 1f; upRefY = 0f
            }
            Surface.ROTATION_180 -> {
                rightRefX = -1f; rightRefY = 0f
                upRefX = 0f; upRefY = -1f
            }
            Surface.ROTATION_270 -> {
                rightRefX = 0f; rightRefY = 1f
                upRefX = -1f; upRefY = 0f
            }
            else -> {
                rightRefX = 1f; rightRefY = 0f
                upRefX = 0f; upRefY = 1f
            }
        }

        val rightDotG = rightRefX * ux + rightRefY * uy
        val refX: Float
        val refY: Float
        if (abs(rightDotG) < 0.92f) {
            refX = rightRefX
            refY = rightRefY
        } else {
            // Fallback if phone is tilted nearly 90° sideways while rotation-locked
            refX = upRefX
            refY = upRefY
        }
        val refDotG = refX * ux + refY * uy
        var rx = refX - refDotG * ux
        var ry = refY - refDotG * uy
        var rz = -refDotG * uz
        val rMag = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1e-4f)
        rx /= rMag; ry /= rMag; rz /= rMag

        // f_hat = g_hat × r_hat (points forward ahead of motorcycle/car across flat 0° to upright 90° mounts)
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

        // Expire stale polar envelope sectors according to configured peakHoldDurationMs
        var hasEnvelopePeak = false
        if (peakHoldDurationMs > 0L) {
            for (i in 0 until PEAK_ENVELOPE_SECTORS) {
                val ts = sectorPeakTsMs[i]
                if (ts > 0L && (elapsedRealtimeMs - ts) > peakHoldDurationMs) {
                    sectorPeakG[i] = 0f
                    sectorPeakTsMs[i] = 0L
                } else if (sectorPeakG[i] >= ENVELOPE_MIN_G) {
                    hasEnvelopePeak = true
                }
            }
        } else {
            for (i in 0 until PEAK_ENVELOPE_SECTORS) {
                if (sectorPeakG[i] >= ENVELOPE_MIN_G) {
                    hasEnvelopePeak = true
                    break
                }
            }
        }

        // Record current directional vector into its 10° polar friction-circle sector
        if (totalHorizG >= ENVELOPE_MIN_G) {
            val angleDeg = (Math.toDegrees(atan2(-cleanLong.toDouble(), cleanLat.toDouble())) + 360.0) % 360.0
            val sectorIdx = (angleDeg / (360.0 / PEAK_ENVELOPE_SECTORS)).roundToInt() % PEAK_ENVELOPE_SECTORS
            if (totalHorizG >= sectorPeakG[sectorIdx]) {
                sectorPeakG[sectorIdx] = totalHorizG
                sectorPeakTsMs[sectorIdx] = elapsedRealtimeMs
            }
            hasEnvelopePeak = true
        }

        val peakExpired = peakHoldDurationMs > 0L &&
            peakTimestampMs > 0L &&
            (elapsedRealtimeMs - peakTimestampMs) > peakHoldDurationMs

        if (totalHorizG >= peakGValue) {
            peakGValue = totalHorizG
            peakLatGValue = cleanLat
            peakLongGValue = cleanLong
            peakTimestampMs = elapsedRealtimeMs
        } else if (peakExpired) {
            // Fall back to the strongest still-unexpired envelope sector, or current live G
            var bestIdx = -1
            var bestVal = totalHorizG
            for (i in 0 until PEAK_ENVELOPE_SECTORS) {
                if (sectorPeakG[i] > bestVal) {
                    bestVal = sectorPeakG[i]
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                val rad = bestIdx * (360.0 / PEAK_ENVELOPE_SECTORS) * PI / 180.0
                peakGValue = bestVal
                peakLatGValue = (cos(rad) * bestVal).toFloat()
                peakLongGValue = (-sin(rad) * bestVal).toFloat()
                peakTimestampMs = sectorPeakTsMs[bestIdx]
            } else {
                peakGValue = totalHorizG
                peakLatGValue = cleanLat
                peakLongGValue = cleanLong
                peakTimestampMs = elapsedRealtimeMs
            }
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
            verticalVibrationG = rawVertG - filteredVertG,
            totalG = totalHorizG,
            total3DG = total3DG,
            peakG = peakGValue,
            peakLateralG = peakLatGValue,
            peakLongitudinalG = peakLongGValue,
            peakEnvelope = if (hasEnvelopePeak) sectorPeakG.toList() else emptyList(),
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
            isAvailable = true,
            displayRotation = displayRotation
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
    private val trackVibrationWindow = TrackVibrationWindow()
    private var captureTrackVibration = false

    private var androidSensorManager: SensorManager? = null
    private var displayManager: DisplayManager? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var isRegistered = false
    private var lastEmitElapsedMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                val rotation = readDefaultDisplayRotation(displayManager)
                synchronized(lock) {
                    fusionEngine.setDisplayRotation(rotation)
                }
                _gForceData.value = _gForceData.value.copy(displayRotation = rotation)
            }
        }
    }

    private val _gForceData = MutableStateFlow(GForceData())
    val gForceData: StateFlow<GForceData> = _gForceData.asStateFlow()

    fun setTrackVibrationCapture(enabled: Boolean) {
        synchronized(lock) {
            trackVibrationWindow.takeRmsG()
            captureTrackVibration = enabled
        }
    }

    fun takeTrackVibrationRmsG(): Float? = synchronized(lock) { trackVibrationWindow.takeRmsG() }

    fun startListening(context: Context, clientId: String) {
        synchronized(lock) {
            val appCtx = context.applicationContext
            activeClients.add(clientId)
            val dm = appCtx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val currentRotation = readDefaultDisplayRotation(dm)
            fusionEngine.setDisplayRotation(currentRotation)

            if (isRegistered) {
                _gForceData.value = _gForceData.value.copy(displayRotation = currentRotation)
                return
            }

            val sm = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            if (sm == null) {
                _gForceData.value = GForceData(
                    hasAccelerometer = false,
                    hasGyroscope = false,
                    isAvailable = false,
                    displayRotation = currentRotation
                )
                return
            }

            androidSensorManager = sm
            displayManager = dm
            accelerometerSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gyroscopeSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

            val accel = accelerometerSensor
            val gyro = gyroscopeSensor

            if (accel == null) {
                _gForceData.value = GForceData(
                    hasAccelerometer = false,
                    hasGyroscope = gyro != null,
                    isAvailable = false,
                    displayRotation = currentRotation
                )
                return
            }

            fusionEngine.reset()
            fusionEngine.setDisplayRotation(currentRotation)
            lastEmitElapsedMs = 0L

            sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            if (gyro != null) {
                sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
            }
            runCatching { dm?.registerDisplayListener(displayListener, mainHandler) }
            isRegistered = true

            _gForceData.value = GForceData(
                hasAccelerometer = true,
                hasGyroscope = gyro != null,
                isGyroFused = false,
                isAvailable = true,
                displayRotation = currentRotation
            )
        }
    }

    fun stopListening(clientId: String) {
        synchronized(lock) {
            activeClients.remove(clientId)
            if (activeClients.isEmpty() && isRegistered) {
                runCatching { androidSensorManager?.unregisterListener(this) }
                runCatching { displayManager?.unregisterDisplayListener(displayListener) }
                isRegistered = false
                fusionEngine.reset()
                lastEmitElapsedMs = 0L
                _gForceData.value = GForceData()
            }
        }
    }

    fun refreshDisplayRotation(context: Context) {
        val dm = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val rotation = readDefaultDisplayRotation(dm)
        synchronized(lock) {
            fusionEngine.setDisplayRotation(rotation)
        }
        _gForceData.value = _gForceData.value.copy(displayRotation = rotation)
    }

    private fun readDefaultDisplayRotation(dm: DisplayManager?): Int {
        return runCatching {
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        }.getOrDefault(Surface.ROTATION_0)
    }

    fun setPeakHoldSeconds(seconds: Int) {
        synchronized(lock) {
            val ms = if (seconds <= 0) 0L else seconds * 1_000L
            fusionEngine.setPeakHoldDurationMs(ms)
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
                peakLateralG = 0f,
                peakLongitudinalG = 0f,
                peakEnvelope = emptyList(),
                trail = emptyList()
            )
        }
        _gForceData.value = updated
    }

    fun resetPeak() {
        val updated = synchronized(lock) {
            fusionEngine.resetPeak()
            val cur = _gForceData.value
            cur.copy(
                peakG = cur.totalG,
                peakLateralG = cur.lateralG,
                peakLongitudinalG = cur.longitudinalG,
                peakEnvelope = emptyList()
            )
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
                    ).also {
                        if (captureTrackVibration) trackVibrationWindow.add(it.verticalVibrationG)
                    }
                }
                if (shouldEmit) {
                    _gForceData.value = snapshot
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
