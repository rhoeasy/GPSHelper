package com.example.track

import android.content.Context
import android.os.SystemClock
import com.example.ble.HeartRateRepository
import com.example.gps.GpsData
import com.example.gps.GpsRepository
import com.example.overlay.FloatingGpsService
import com.example.sensor.EnvironmentSensorManager
import com.example.sensor.GForceRepository
import com.example.sensor.HikingStepSensor
import com.example.sensor.MotionSensorManager
import com.example.sensor.MotorcycleLeanReadout
import com.example.sensor.MotorcycleLeanSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    SAVING
}

data class RecordingSnapshot(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val mode: ActivityMode = ActivityMode.KARTING,
    val elapsedMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val minAltitudeMeters: Double? = null,
    val maxAltitudeMeters: Double? = null,
    val currentLapNumber: Int = 1,
    val currentLapElapsedMs: Long? = null,
    val bestLapMs: Long? = null,
    val lastLapMs: Long? = null,
    val hasStartGate: Boolean = false,
    val currentHeartRate: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val peakTotalG: Float = 0f,
    val peakLateralG: Float = 0f,
    val pointCount: Int = 0,
    val stepCount: Int? = null,
    val motorcycleLean: MotorcycleLeanReadout = MotorcycleLeanReadout()
) {
    val isActive: Boolean
        get() = status != RecordingStatus.IDLE

    val showMotorcycleLean: Boolean
        get() = status == RecordingStatus.RECORDING && mode == ActivityMode.OFFROAD_CROSSING
}

/**
 * Live offline GPS + BLE Heart Rate + Hardware G-Force track recorder supporting:
 * - Karting mode: automatic start/finish gate lap detection, per-lap HR & peak G
 * - Hiking mode: smoothed altitude profile, elevation gain/loss, distance, pace & HR
 */
object TrackRecorder {
    private const val CLIENT_ID = "track_recorder"
    private const val MAX_ACCURACY_METERS = 35f
    private const val BAROMETER_CALIBRATION_VERTICAL_ACCURACY_M = 20f
    private const val MIN_STEP_DISTANCE_M = 1.5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _snapshot = MutableStateFlow(RecordingSnapshot())
    val snapshot: StateFlow<RecordingSnapshot> = _snapshot.asStateFlow()

    private var gpsCollectJob: Job? = null
    private var timerJob: Job? = null

    private val recordedPoints = mutableListOf<TrackPointEntity>()
    private var elevation = TrackElevation()

    private var sessionStartWallMs: Long = 0L
    private var sessionStartElapsedMs: Long = 0L
    private var accumulatedActiveMs: Long = 0L
    private var activeSegmentStartElapsedMs: Long = 0L

    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var lastFixElapsedMs: Long = 0L
    private var lastTrackPointFixElapsedMs: Long = 0L

    private var totalDistanceM: Double = 0.0
    private var maxSpeedMps: Float = 0f

    private var hrSum: Long = 0L
    private var hrCount: Int = 0
    private var maxHr: Int? = null
    private var peakTotalG: Float = 0f
    private var peakLateralG: Float = 0f

    private var selectedKartingCourse: KartingTimingCourse? = null
    private var kartingTiming: KartingTrackTiming? = null
    private var kartingBreakBeforeNext = true

    fun selectMode(mode: ActivityMode) {
        synchronized(lock) {
            if (_snapshot.value.status == RecordingStatus.IDLE) {
                _snapshot.update { it.copy(mode = mode, hasStartGate = mode == ActivityMode.KARTING && selectedKartingCourse != null) }
            }
        }
    }

    fun startRecording(context: Context, mode: ActivityMode = _snapshot.value.mode) {
        synchronized(lock) {
            if (_snapshot.value.status != RecordingStatus.IDLE) return
            resetStateLocked()
            sessionStartWallMs = System.currentTimeMillis()
            sessionStartElapsedMs = SystemClock.elapsedRealtime()
            activeSegmentStartElapsedMs = sessionStartElapsedMs
            val course = selectedKartingCourse.takeIf { mode == ActivityMode.KARTING }
            kartingTiming = course?.let { KartingTrackTiming(it) }
            if (mode == ActivityMode.HIKING) HikingStepSensor.start(context.applicationContext)
            if (mode == ActivityMode.OFFROAD_CROSSING) MotorcycleLeanSensor.start(context.applicationContext)
            _snapshot.value = RecordingSnapshot(
                status = RecordingStatus.RECORDING,
                mode = mode,
                hasStartGate = course != null,
                stepCount = HikingStepSensor.currentCount(),
                motorcycleLean = if (mode == ActivityMode.OFFROAD_CROSSING) MotorcycleLeanSensor.current() else MotorcycleLeanReadout()
            )
        }

        EnvironmentSensorManager.startPressureListening(context.applicationContext, CLIENT_ID)
        GForceRepository.startListening(context.applicationContext, CLIENT_ID)
        MotionSensorManager.setTrackVibrationCapture(true)
        GpsRepository.startTracking(context.applicationContext, CLIENT_ID)
        FloatingGpsService.startRecordingKeepAlive(context.applicationContext)

        gpsCollectJob?.cancel()
        gpsCollectJob = scope.launch {
            GpsRepository.gpsData.collect { data ->
                onGpsSample(data)
            }
        }

        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(200L)
                synchronized(lock) {
                    if (_snapshot.value.status == RecordingStatus.RECORDING) {
                        val g = GForceRepository.gForceData.value
                        if (g.totalG > peakTotalG) peakTotalG = g.totalG
                        if (abs(g.lateralG) > peakLateralG) peakLateralG = abs(g.lateralG)
                        publishSnapshotLocked()
                    }
                }
            }
        }
    }

    fun pauseRecording() {
        synchronized(lock) {
            if (_snapshot.value.status != RecordingStatus.RECORDING) return
            accumulatedActiveMs += (SystemClock.elapsedRealtime() - activeSegmentStartElapsedMs)
                .coerceAtLeast(0L)
            activeSegmentStartElapsedMs = 0L
            elevation.breakInterval()
            lastTrackPointFixElapsedMs = 0L
            breakKartingTimingLocked()
            MotionSensorManager.setTrackVibrationCapture(false)
            HikingStepSensor.pause()
            _snapshot.update { it.copy(status = RecordingStatus.PAUSED) }
            publishSnapshotLocked()
        }
    }

    fun resumeRecording() {
        synchronized(lock) {
            if (_snapshot.value.status != RecordingStatus.PAUSED) return
            activeSegmentStartElapsedMs = SystemClock.elapsedRealtime()
            MotionSensorManager.setTrackVibrationCapture(true)
            HikingStepSensor.resume()
            _snapshot.update { it.copy(status = RecordingStatus.RECORDING) }
            publishSnapshotLocked()
        }
    }

    fun setKartingCourse(course: KartingTimingCourse?): Boolean {
        synchronized(lock) {
            if (_snapshot.value.status != RecordingStatus.IDLE || _snapshot.value.mode != ActivityMode.KARTING) return false
            selectedKartingCourse = course
            publishSnapshotLocked()
            return true
        }
    }

    fun zeroMotorcycleLean() {
        synchronized(lock) {
            if (!_snapshot.value.showMotorcycleLean) return
            MotorcycleLeanSensor.zero()
            publishSnapshotLocked()
        }
    }

    suspend fun stopAndSave(context: Context): Long? {
        val toSave: Triple<TrackSessionEntity, List<TrackPointEntity>, List<TrackLapEntity>>?
        val mode: ActivityMode

        synchronized(lock) {
            val snapshot = _snapshot.value
            if (snapshot.status == RecordingStatus.IDLE || snapshot.status == RecordingStatus.SAVING) return null

            val totalDurationMs = computeActiveElapsedMsLocked()
            mode = snapshot.mode
            HikingStepSensor.pause()
            _snapshot.value = snapshot.copy(status = RecordingStatus.SAVING, elapsedMs = totalDurationMs)
            val timing = kartingTiming
            val pointsCopy = timing?.labelPoints(recordedPoints) ?: recordedPoints.toList()
            val lapsCopy = timing?.laps?.toList().orEmpty()

            val endWallMs = System.currentTimeMillis()
            val avgSpeed = if (totalDurationMs > 1_000L) {
                (totalDistanceM / (totalDurationMs / 1000.0)).toFloat()
            } else {
                0f
            }
            val avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null

            toSave = if (pointsCopy.size >= 2) {
                val session = TrackSessionEntity(
                    name = TrackFormatters.formatSessionTitle(mode, sessionStartWallMs),
                    mode = mode.name,
                    startTimeMs = sessionStartWallMs,
                    endTimeMs = endWallMs,
                    durationMs = totalDurationMs,
                    distanceMeters = totalDistanceM,
                    maxSpeedMps = maxSpeedMps,
                    avgSpeedMps = avgSpeed,
                    elevationGainMeters = elevation.gainMeters,
                    elevationLossMeters = elevation.lossMeters,
                    minAltitudeMeters = elevation.minMeters,
                    maxAltitudeMeters = elevation.maxMeters,
                    lapCount = lapsCopy.size,
                    bestLapMs = lapsCopy.minOfOrNull { it.lapDurationMs },
                    avgHeartRate = avgHr,
                    maxHeartRate = maxHr,
                    pointCount = pointsCopy.size,
                    peakTotalG = peakTotalG,
                    peakLateralG = peakLateralG,
                    kartingCourseJson = selectedKartingCourse?.takeIf { timing != null }?.encode()
                )
                Triple(session, pointsCopy, lapsCopy)
            } else {
                null
            }

            gpsCollectJob?.cancel()
            gpsCollectJob = null
            timerJob?.cancel()
            timerJob = null
        }

        try {
            GpsRepository.stopTracking(CLIENT_ID)
            EnvironmentSensorManager.stopPressureListening(CLIENT_ID)
            MotionSensorManager.setTrackVibrationCapture(false)
            GForceRepository.stopListening(CLIENT_ID)

            val (session, points, laps) = toSave ?: return null
            val stepCount = HikingStepSensor.awaitFinalCount()
            return withContext(Dispatchers.IO) {
                TrackDatabase.getInstance(context)
                    .trackDao()
                    .saveCompleteSession(session.copy(stepCount = stepCount), points, laps)
            }
        } finally {
            HikingStepSensor.stop()
            if (mode == ActivityMode.OFFROAD_CROSSING) MotorcycleLeanSensor.stop()
            FloatingGpsService.stopRecordingKeepAlive(context.applicationContext)
            synchronized(lock) {
                resetStateLocked()
                selectedKartingCourse = null
                _snapshot.value = RecordingSnapshot(mode = mode)
            }
        }
    }

    private fun onGpsSample(data: GpsData) {
        synchronized(lock) {
            if (_snapshot.value.status != RecordingStatus.RECORDING) return
            val isKart = _snapshot.value.mode == ActivityMode.KARTING
            if (!data.hasFix) {
                elevation.breakInterval()
                if (isKart) breakKartingTimingLocked()
                return
            }
            val lat = data.latitude ?: return
            val lng = data.longitude ?: return
            if (data.lastUpdateElapsedRealtimeMs <= lastFixElapsedMs && lastFixElapsedMs != 0L) return
            if (isKart && data.lastUpdateElapsedRealtimeMs < activeSegmentStartElapsedMs) return

            val accuracy = data.accuracyMeters
            if (accuracy != null && accuracy > MAX_ACCURACY_METERS && recordedPoints.isNotEmpty()) {
                if (isKart) breakKartingTimingLocked()
                return
            }

            if (lastFixElapsedMs != 0L &&
                data.lastUpdateElapsedRealtimeMs - lastFixElapsedMs >= GpsRepository.GPS_FRESHNESS_WINDOW_MS
            ) {
                elevation.breakInterval()
                if (isKart) breakKartingTimingLocked()
            }
            lastFixElapsedMs = data.lastUpdateElapsedRealtimeMs
            val speed = (data.speedMps ?: 0f).coerceAtLeast(0f)
            val prevLat = lastLat
            val prevLng = lastLng

            val stepMeters = if (prevLat != null && prevLng != null) {
                haversineMeters(prevLat, prevLng, lat, lng)
            } else {
                0.0
            }

            if (!isKart && prevLat != null && stepMeters < MIN_STEP_DISTANCE_M && speed < 0.4f) {
                return
            }

            lastLat = lat
            lastLng = lng
            if (!isKart || !kartingBreakBeforeNext) totalDistanceM += stepMeters

            if (speed > maxSpeedMps) maxSpeedMps = speed

            val legDurationMs = data.lastUpdateElapsedRealtimeMs - lastTrackPointFixElapsedMs
            val isContinuous = lastTrackPointFixElapsedMs != 0L && legDurationMs in 1L..MAX_CONTINUOUS_TRACK_INTERVAL_MS
            lastTrackPointFixElapsedMs = data.lastUpdateElapsedRealtimeMs
            val pressure = EnvironmentSensorManager.telemetry.value.freshPressureHpa(SystemClock.elapsedRealtime())
            val horizontalAccuracyGood = accuracy?.let { it in 0f..MAX_ACCURACY_METERS }
            val verticalAccuracyGood = data.verticalAccuracyMeters?.let { it in 0f..BAROMETER_CALIBRATION_VERTICAL_ACCURACY_M }
            val gpsAltitude = data.acceptedAltitudeMeters.takeUnless {
                pressure != null && (horizontalAccuracyGood == false || verticalAccuracyGood == false)
            }
            val altitude = elevation.record(gpsAltitude, pressure, horizontalAccuracyGood == true && verticalAccuracyGood == true)
            val vibrationG = MotionSensorManager.takeTrackVibrationRmsG()
                ?.takeIf { isContinuous && speed >= 1f && stepMeters >= 0.5 }

            // Fuse live BLE Heart Rate directly with this motion sample
            val hrState = HeartRateRepository.heartRateState.value
            val currentBpm = if (hrState.isConnected) hrState.bpm else null
            if (currentBpm != null) {
                hrSum += currentBpm
                hrCount++
                maxHr = maxHr?.let { maxOf(it, currentBpm) } ?: currentBpm
            }

            // Fuse live hardware G-Force directly with this motion sample
            val gState = GForceRepository.gForceData.value
            if (gState.totalG > peakTotalG) peakTotalG = gState.totalG
            if (abs(gState.lateralG) > peakLateralG) peakLateralG = abs(gState.lateralG)
            val point =
                TrackPointEntity(
                    sessionId = 0L,
                    timestampMs = if (isKart) sessionStartWallMs + data.lastUpdateElapsedRealtimeMs - sessionStartElapsedMs
                        else System.currentTimeMillis(),
                    latitude = lat,
                    longitude = lng,
                    altitudeMeters = altitude?.meters,
                    speedMps = speed,
                    accuracyMeters = accuracy,
                    bearingDegrees = data.bearingDegrees,
                    heartRateBpm = currentBpm,
                    lapNumber = if (isKart) 0 else 1,
                    lateralG = gState.lateralG,
                    longitudinalG = gState.longitudinalG,
                    totalG = gState.totalG,
                    altitudeFromBarometer = altitude?.fromBarometer,
                    verticalVibrationG = vibrationG,
                    fixElapsedMs = data.lastUpdateElapsedRealtimeMs.takeIf { isKart },
                    timingBreakBefore = kartingBreakBeforeNext.takeIf { isKart },
                    speedUsable = data.speedMps != null
                )
            if (isKart) {
                kartingTiming?.addPoint(recordedPoints.size, point)
                kartingBreakBeforeNext = false
            }
            recordedPoints.add(point)

            publishSnapshotLocked()
        }
    }

    private fun breakKartingTimingLocked() {
        kartingTiming?.interrupt()
        kartingBreakBeforeNext = true
    }

    private fun computeActiveElapsedMsLocked(): Long {
        val runningDelta = if (_snapshot.value.status == RecordingStatus.RECORDING &&
            activeSegmentStartElapsedMs > 0L
        ) {
            (SystemClock.elapsedRealtime() - activeSegmentStartElapsedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        return accumulatedActiveMs + runningDelta
    }

    private fun publishSnapshotLocked() {
        val elapsed = computeActiveElapsedMsLocked()
        val avgSpeed = if (elapsed > 1_000L) {
            (totalDistanceM / (elapsed / 1000.0)).toFloat()
        } else {
            0f
        }
        val avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else null
        val liveHr = HeartRateRepository.heartRateState.value.let { if (it.isConnected) it.bpm else null }
        val laps = kartingTiming?.laps.orEmpty()

        _snapshot.value = RecordingSnapshot(
            status = _snapshot.value.status,
            mode = _snapshot.value.mode,
            elapsedMs = elapsed,
            distanceMeters = totalDistanceM,
            maxSpeedMps = maxSpeedMps,
            avgSpeedMps = avgSpeed,
            elevationGainMeters = elevation.gainMeters,
            elevationLossMeters = elevation.lossMeters,
            minAltitudeMeters = elevation.minMeters,
            maxAltitudeMeters = elevation.maxMeters,
            currentLapNumber = laps.size + 1,
            currentLapElapsedMs = kartingTiming?.startedAtMs?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) },
            bestLapMs = laps.minOfOrNull { it.lapDurationMs },
            lastLapMs = laps.lastOrNull()?.lapDurationMs,
            hasStartGate = _snapshot.value.mode == ActivityMode.KARTING && selectedKartingCourse != null,
            currentHeartRate = liveHr,
            avgHeartRate = avgHr,
            maxHeartRate = maxHr,
            peakTotalG = peakTotalG,
            peakLateralG = peakLateralG,
            pointCount = recordedPoints.size,
            stepCount = HikingStepSensor.currentCount(),
            motorcycleLean = if (_snapshot.value.showMotorcycleLean) MotorcycleLeanSensor.current() else MotorcycleLeanReadout()
        )
    }

    private fun resetStateLocked() {
        recordedPoints.clear()
        elevation = TrackElevation()
        sessionStartWallMs = 0L
        sessionStartElapsedMs = 0L
        accumulatedActiveMs = 0L
        activeSegmentStartElapsedMs = 0L
        lastLat = null
        lastLng = null
        lastFixElapsedMs = 0L
        lastTrackPointFixElapsedMs = 0L
        totalDistanceM = 0.0
        maxSpeedMps = 0f
        hrSum = 0L
        hrCount = 0
        maxHr = null
        peakTotalG = 0f
        peakLateralG = 0f
        kartingTiming = null
        kartingBreakBeforeNext = true
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2) + cos(radLat1) * cos(radLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
