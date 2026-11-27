package com.example.track

import android.content.Context
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Generates realistic example Karting (3-lap circuit) and Hiking (mountain loop) tracks
 * so first-time users can immediately preview the vector map, elevation/speed/HR profile charts,
 * lap split table, and GPX/JSON exports.
 */
object SampleTrackSeeder {
    private const val PREFS_NAME = "track_sample_prefs"
    private const val KEY_SEEDED_V1 = "sample_tracks_seeded_v1"
    private const val KEY_SEEDED_OFFROAD_V1 = "sample_offroad_seeded_v1"
    private const val KEY_SEEDED_KARTING_F1_V2 = "sample_karting_f1_v2"

    private const val LEGACY_SAMPLE_KARTING_NAME = "卡丁车 · 示例赛道（3 圈）"
    const val SAMPLE_KARTING_NAME = "卡丁车 · 示例赛道（4 圈）"
    const val SAMPLE_HIKING_NAME = "徒步 · 示例山野环线"
    const val SAMPLE_OFFROAD_NAME = "越野穿越 · 示例翻山穿越"

    private const val METERS_PER_DEG_LAT = 111_320.0

    suspend fun seedOnceIfNeeded(context: Context, dao: TrackDao): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nowMs = System.currentTimeMillis()
        var seededAny = false

        if (!prefs.getBoolean(KEY_SEEDED_V1, false)) {
            val karting = buildSampleKartingTrack(endTimeMs = nowMs - 2 * 3_600_000L)
            val hiking = buildSampleHikingTrack(endTimeMs = nowMs - 26 * 3_600_000L)
            dao.saveCompleteSession(karting.session, karting.points, karting.laps)
            dao.saveCompleteSession(hiking.session, hiking.points, hiking.laps)
            prefs.edit()
                .putBoolean(KEY_SEEDED_V1, true)
                .putBoolean(KEY_SEEDED_KARTING_F1_V2, true)
                .apply()
            seededAny = true
        } else if (!prefs.getBoolean(KEY_SEEDED_KARTING_F1_V2, false)) {
            // Upgrade legacy 3-lap Karting sample to 4-lap F1 Purple/Green/Yellow showcase without touching user tracks
            dao.deleteSessionsByName(LEGACY_SAMPLE_KARTING_NAME)
            val karting = buildSampleKartingTrack(endTimeMs = nowMs - 2 * 3_600_000L)
            dao.saveCompleteSession(karting.session, karting.points, karting.laps)
            prefs.edit().putBoolean(KEY_SEEDED_KARTING_F1_V2, true).apply()
            seededAny = true
        }

        if (!prefs.getBoolean(KEY_SEEDED_OFFROAD_V1, false)) {
            val offroad = buildSampleOffroadCrossingTrack(endTimeMs = nowMs - 14 * 3_600_000L)
            dao.saveCompleteSession(offroad.session, offroad.points, offroad.laps)
            prefs.edit().putBoolean(KEY_SEEDED_OFFROAD_V1, true).apply()
            seededAny = true
        }

        return seededAny
    }

    /**
     * Builds a realistic 3-lap outdoor karting circuit (~885 m/lap) with main straight,
     * heavy braking hairpins, fast S-chicane, speed-correlated G-force, and rising heart rate.
     */
    fun buildSampleKartingTrack(endTimeMs: Long): TrackSessionDetail {
        val originLat = 39.982400
        val originLon = 116.486200
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(originLat))

        // Closed circuit control waypoints in local meters (x = East, y = North)
        // Main straight along bottom -> Turn 1 hairpin -> back S-bend -> sweeping parabola -> final corner
        val controlLoop = listOf(
            0.0 to 0.0,
            115.0 to 0.0,
            215.0 to 8.0,
            255.0 to 46.0,
            230.0 to 92.0,
            162.0 to 96.0,
            128.0 to 136.0,
            165.0 to 188.0,
            110.0 to 228.0,
            24.0 to 216.0,
            -42.0 to 172.0,
            -26.0 to 112.0,
            -72.0 to 62.0,
            -52.0 to 12.0
        )

        val denseLoop = sampleClosedCatmullRom(controlLoop, samplesPerSegment = 5)
        // 4-lap showcase crafted so Best Lap (L3) exhibits all 3 F1 colors:
        // L1: warmup (Y/Y/Y) | L2: S1 Green, S2 Purple, S3 Yellow | L3 (Best): S1 Purple, S2 Yellow, S3 Green | L4: S1 Yellow, S2 Yellow, S3 Purple
        val lapTargetsMs = listOf(56_400L, 53_850L, 52_200L, 52_650L)
        val lapSpeedScale = listOf(0.93f, 0.98f, 1.03f, 1.01f)
        val lapBaseHr = listOf(145, 156, 166, 169)

        val totalDurationMs = lapTargetsMs.sum()
        val startTimeMs = endTimeMs - totalDurationMs

        val points = mutableListOf<TrackPointEntity>()
        val laps = mutableListOf<TrackLapEntity>()

        var cursorMs = startTimeMs
        var totalDist = 0.0
        var maxSpeed = 0f
        var minAlt = Double.POSITIVE_INFINITY
        var maxAlt = Double.NEGATIVE_INFINITY
        var elevGain = 0.0
        var elevLoss = 0.0
        var prevCountedAlt: Double? = null
        var peakTotalG = 0f
        var peakLatG = 0f
        var hrSum = 0L
        var hrCount = 0
        var maxHr = 0

        for (lapIdx in lapTargetsMs.indices) {
            val lapNumber = lapIdx + 1
            val lapDurationMs = lapTargetsMs[lapIdx]
            val speedFactor = lapSpeedScale[lapIdx]
            val baseHr = lapBaseHr[lapIdx]
            val n = denseLoop.size

            // Precompute curvature at every point on the circuit for realistic braking & cornering
            val segLengths = DoubleArray(n)
            val curvatures = DoubleArray(n)
            for (i in 0 until n) {
                val prev = denseLoop[(i - 1 + n) % n]
                val curr = denseLoop[i]
                val next = denseLoop[(i + 1) % n]
                segLengths[i] = hypot(next.first - curr.first, next.second - curr.second)
                val v1x = curr.first - prev.first
                val v1y = curr.second - prev.second
                val v2x = next.first - curr.first
                val v2y = next.second - curr.second
                val cross = v1x * v2y - v1y * v2x
                val dot = v1x * v2x + v1y * v2y
                val angle = atan2(cross, dot)
                val avgLen = (hypot(v1x, v1y) + hypot(v2x, v2y)).coerceAtLeast(1.0) * 0.5
                curvatures[i] = angle / avgLen // signed curvature (1/m)
            }

            // Compute raw speeds inversely proportional to local curvature (straights fast, hairpins slow)
            // with realistic per-sector pace variation so F1 Purple / Green / Yellow sectors shine
            val rawSpeeds = FloatArray(n)
            for (i in 0 until n) {
                val localAbsCurv = (
                    abs(curvatures[(i - 1 + n) % n]) +
                        2.0 * abs(curvatures[i]) +
                        abs(curvatures[(i + 1) % n])
                    ) / 4.0
                val lapProgress = i.toDouble() / n.toDouble()
                val sectorBias = when (lapIdx) {
                    0 -> 1.0 // Lap 1 baseline warmup (Yellow / Yellow / Yellow)
                    1 -> when {
                        lapProgress < 0.34 -> 1.04 // Lap 2 S1 faster than L1, slower than L3 (Green!)
                        lapProgress < 0.67 -> 1.18 // Lap 2 nails S2 overall fastest (Purple!)
                        else -> 0.88 // Lap 2 S3 traffic slower than L1 (Yellow!)
                    }
                    2 -> when {
                        lapProgress < 0.34 -> 1.16 // Lap 3 (Best Lap) dominates S1 overall fastest (Purple!)
                        lapProgress < 0.67 -> 0.92 // Lap 3 S2 slower than L2's purple S2 (Yellow!)
                        else -> 1.04 // Lap 3 S3 beats L1 & L2, later beaten by L4 (Green!)
                    }
                    else -> when {
                        lapProgress < 0.34 -> 0.94 // Lap 4 S1 slower than L3 (Yellow!)
                        lapProgress < 0.67 -> 0.95 // Lap 4 S2 slower than L2 (Yellow!)
                        else -> 1.18 // Lap 4 nails S3 overall fastest (Purple!)
                    }
                }
                val targetSpeedMps = (21.4 - localAbsCurv * 230.0).coerceIn(7.8, 21.2) * speedFactor * sectorBias
                rawSpeeds[i] = targetSpeedMps.toFloat()
            }

            // Normalize segment times to match target lap duration exactly
            val rawDtSec = DoubleArray(n) { i -> segLengths[i] / rawSpeeds[i].coerceAtLeast(1f) }
            val rawLapSec = rawDtSec.sum().coerceAtLeast(1.0)
            val timeScale = (lapDurationMs / 1000.0) / rawLapSec

            var lapDist = 0.0
            var lapMaxSpeed = 0f
            var lapPeakG = 0f
            var lapHrSum = 0
            var lapHrCount = 0

            for (i in 0 until n) {
                val (x, y) = denseLoop[i]
                val (nx, ny) = denseLoop[(i + 1) % n]
                val lat = originLat + (y / METERS_PER_DEG_LAT)
                val lon = originLon + (x / metersPerDegLon)

                val dtSec = (rawDtSec[i] * timeScale).coerceAtLeast(0.15)
                val actualSpeed = (segLengths[i] / dtSec).toFloat().coerceIn(6.5f, 22.8f)
                val prevSpeed = if (i > 0) (segLengths[i - 1] / (rawDtSec[i - 1] * timeScale)).toFloat() else actualSpeed

                // Bearing in degrees (0 = North, 90 = East)
                val bearing = ((Math.toDegrees(atan2(nx - x, ny - y)) + 360.0) % 360.0).toFloat()

                // Realistic G-forces from centripetal acceleration (v^2 * curvature / g) and longitudinal accel
                val latG = ((actualSpeed * actualSpeed * curvatures[i]) / 9.80665)
                    .toFloat()
                    .coerceIn(-1.55f, 1.55f)
                val lonG = (((actualSpeed - prevSpeed) / dtSec) / 9.80665)
                    .toFloat()
                    .coerceIn(-1.15f, 0.85f)
                val totalG = sqrt(latG * latG + lonG * lonG)

                // Subtle circuit camber elevation (~42.0m - 44.6m)
                val alt = 42.5 + 1.4 * sin(2.0 * PI * i / n) + 0.6 * cos(4.0 * PI * i / n)
                if (alt < minAlt) minAlt = alt
                if (alt > maxAlt) maxAlt = alt
                val prevC = prevCountedAlt
                if (prevC == null) {
                    prevCountedAlt = alt
                } else if (abs(alt - prevC) >= 1.5) {
                    if (alt > prevC) elevGain += (alt - prevC) else elevLoss += (prevC - alt)
                    prevCountedAlt = alt
                }

                // Heart rate rises slightly during braking/cornering effort and across laps
                val hr = (baseHr + (totalG * 7.5f).roundToInt() + (i * 5 / n)).coerceIn(132, 179)

                points.add(
                    TrackPointEntity(
                        sessionId = 0L,
                        timestampMs = cursorMs,
                        latitude = lat,
                        longitude = lon,
                        altitudeMeters = alt,
                        speedMps = actualSpeed,
                        accuracyMeters = 2.4f,
                        bearingDegrees = bearing,
                        heartRateBpm = hr,
                        lapNumber = lapNumber,
                        lateralG = latG,
                        longitudinalG = lonG,
                        totalG = totalG
                    )
                )

                lapDist += segLengths[i]
                totalDist += segLengths[i]
                if (actualSpeed > lapMaxSpeed) lapMaxSpeed = actualSpeed
                if (actualSpeed > maxSpeed) maxSpeed = actualSpeed
                if (totalG > lapPeakG) lapPeakG = totalG
                if (totalG > peakTotalG) peakTotalG = totalG
                if (abs(latG) > peakLatG) peakLatG = abs(latG)
                lapHrSum += hr
                lapHrCount++
                hrSum += hr
                hrCount++
                if (hr > maxHr) maxHr = hr

                cursorMs += (dtSec * 1000.0).toLong()
            }

            laps.add(
                TrackLapEntity(
                    sessionId = 0L,
                    lapNumber = lapNumber,
                    lapDurationMs = lapDurationMs,
                    lapDistanceMeters = lapDist,
                    maxSpeedMps = lapMaxSpeed,
                    avgHeartRate = if (lapHrCount > 0) lapHrSum / lapHrCount else null,
                    peakG = lapPeakG
                )
            )
        }

        val actualDurationMs = (cursorMs - startTimeMs).coerceAtLeast(1L)
        val avgSpeedMps = (totalDist / (actualDurationMs / 1000.0)).toFloat()

        val session = TrackSessionEntity(
            name = SAMPLE_KARTING_NAME,
            mode = ActivityMode.KARTING.name,
            startTimeMs = startTimeMs,
            endTimeMs = cursorMs,
            durationMs = actualDurationMs,
            distanceMeters = totalDist,
            maxSpeedMps = maxSpeed,
            avgSpeedMps = avgSpeedMps,
            elevationGainMeters = elevGain,
            elevationLossMeters = elevLoss,
            minAltitudeMeters = minAlt.takeIf { it.isFinite() },
            maxAltitudeMeters = maxAlt.takeIf { it.isFinite() },
            lapCount = laps.size,
            bestLapMs = laps.minOfOrNull { it.lapDurationMs },
            avgHeartRate = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHeartRate = maxHr.takeIf { it > 0 },
            pointCount = points.size,
            peakTotalG = peakTotalG,
            peakLateralG = peakLatG
        )
        return TrackSessionDetail(session = session, points = points, laps = laps)
    }

    /**
     * Builds a realistic scenic mountain loop hike (~5.4 km, ~335m elevation gain, ~1h25m)
     * with switchback ascent, panoramic summit ridge, valley descent, and slope-correlated heart rate.
     */
    fun buildSampleHikingTrack(endTimeMs: Long): TrackSessionDetail {
        val originLat = 39.994500
        val originLon = 116.188000
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(originLat))

        // Mountain loop waypoints in local meters (x = East, y = North)
        val loopWaypoints = listOf(
            0.0 to 0.0,
            240.0 to 180.0,
            460.0 to 390.0,
            610.0 to 690.0,
            880.0 to 880.0,
            1180.0 to 1090.0,
            1420.0 to 1320.0,
            1340.0 to 1660.0,
            1060.0 to 1850.0,
            720.0 to 1760.0,
            420.0 to 1540.0,
            190.0 to 1210.0,
            -60.0 to 890.0,
            -140.0 to 520.0,
            -70.0 to 210.0
        )

        val denseTrail = sampleClosedCatmullRom(loopWaypoints, samplesPerSegment = 10)
        val n = denseTrail.size
        val totalDurationMs = 5_100_000L // 1h 25m
        val startTimeMs = endTimeMs - totalDurationMs

        val points = mutableListOf<TrackPointEntity>()

        var cursorMs = startTimeMs
        var totalDist = 0.0
        var maxSpeed = 0f
        var minAlt = Double.POSITIVE_INFINITY
        var maxAlt = Double.NEGATIVE_INFINITY
        var elevGain = 0.0
        var elevLoss = 0.0
        var prevCountedAlt: Double? = null
        var peakTotalG = 0f
        var peakLatG = 0f
        var hrSum = 0L
        var hrCount = 0
        var maxHr = 0

        // Elevation profile along normalized progress p in [0, 1]:
        // Trailhead 318m -> climb to summit 652m around p=0.46 -> ridge walk -> valley descent back to 318m
        fun altitudeAt(progress: Double): Double {
            val mainBell = sin(PI * progress)
            val secondaryRidge = 0.16 * sin(3.0 * PI * progress)
            val microTrail = 6.5 * sin(10.0 * PI * progress)
            return 318.0 + 326.0 * (mainBell * mainBell * 0.65 + mainBell * 0.35 + secondaryRidge).coerceAtLeast(0.0) + microTrail
        }

        val segLengths = DoubleArray(n) { i ->
            val curr = denseTrail[i]
            val next = denseTrail[(i + 1) % n]
            hypot(next.first - curr.first, next.second - curr.second)
        }

        val alts = DoubleArray(n) { i -> altitudeAt(i.toDouble() / n) }
        val rawDtSec = DoubleArray(n) { i ->
            val slope = (alts[(i + 1) % n] - alts[i]) / segLengths[i].coerceAtLeast(1.0)
            // Slower on steep uphills, brisker on ridge & gentle downhills
            val walkSpeedMps = (1.18 - slope * 2.1).coerceIn(0.75, 1.55)
            segLengths[i] / walkSpeedMps
        }
        val rawTotalSec = rawDtSec.sum().coerceAtLeast(1.0)
        val timeScale = (totalDurationMs / 1000.0) / rawTotalSec

        for (i in 0 until n) {
            val (x, y) = denseTrail[i]
            val (nx, ny) = denseTrail[(i + 1) % n]
            val lat = originLat + (y / METERS_PER_DEG_LAT)
            val lon = originLon + (x / metersPerDegLon)
            val alt = alts[i]
            val nextAlt = alts[(i + 1) % n]

            val dtSec = (rawDtSec[i] * timeScale).coerceAtLeast(5.0)
            val speedMps = (segLengths[i] / dtSec).toFloat().coerceIn(0.65f, 1.85f)
            val bearing = ((Math.toDegrees(atan2(nx - x, ny - y)) + 360.0) % 360.0).toFloat()

            val grade = (nextAlt - alt) / segLengths[i].coerceAtLeast(1.0)
            val hr = (116 + (grade * 210.0).roundToInt() + (12.0 * sin(PI * i / n)).roundToInt())
                .coerceIn(104, 158)

            val latG = (0.04f * sin(i * 0.6)).toFloat()
            val lonG = (0.05f * cos(i * 0.4)).toFloat()
            val totalG = (0.12f + abs(latG) + abs(lonG)).coerceAtMost(0.28f)

            if (alt < minAlt) minAlt = alt
            if (alt > maxAlt) maxAlt = alt
            val prevC = prevCountedAlt
            if (prevC == null) {
                prevCountedAlt = alt
            } else if (abs(alt - prevC) >= 2.0) {
                if (alt > prevC) elevGain += (alt - prevC) else elevLoss += (prevC - alt)
                prevCountedAlt = alt
            }

            points.add(
                TrackPointEntity(
                    sessionId = 0L,
                    timestampMs = cursorMs,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = alt,
                    speedMps = speedMps,
                    accuracyMeters = 3.6f,
                    bearingDegrees = bearing,
                    heartRateBpm = hr,
                    lapNumber = 0,
                    lateralG = latG,
                    longitudinalG = lonG,
                    totalG = totalG
                )
            )

            totalDist += segLengths[i]
            if (speedMps > maxSpeed) maxSpeed = speedMps
            if (totalG > peakTotalG) peakTotalG = totalG
            if (abs(latG) > peakLatG) peakLatG = abs(latG)
            hrSum += hr
            hrCount++
            if (hr > maxHr) maxHr = hr

            cursorMs += (dtSec * 1000.0).toLong()
        }

        val actualDurationMs = (cursorMs - startTimeMs).coerceAtLeast(1L)
        val avgSpeedMps = (totalDist / (actualDurationMs / 1000.0)).toFloat()

        val session = TrackSessionEntity(
            name = SAMPLE_HIKING_NAME,
            mode = ActivityMode.HIKING.name,
            startTimeMs = startTimeMs,
            endTimeMs = cursorMs,
            durationMs = actualDurationMs,
            distanceMeters = totalDist,
            maxSpeedMps = maxSpeed,
            avgSpeedMps = avgSpeedMps,
            elevationGainMeters = elevGain,
            elevationLossMeters = elevLoss,
            minAltitudeMeters = minAlt.takeIf { it.isFinite() },
            maxAltitudeMeters = maxAlt.takeIf { it.isFinite() },
            lapCount = 0,
            bestLapMs = null,
            avgHeartRate = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHeartRate = maxHr.takeIf { it > 0 },
            pointCount = points.size,
            peakTotalG = peakTotalG,
            peakLateralG = peakLatG
        )
        return TrackSessionDetail(session = session, points = points, laps = emptyList())
    }

    /**
     * Builds a realistic point-to-point motorcycle mountain-crossing route (~16.5 km, ~740m gain,
     * ~34 min) climbing over Mountain Pass A (845 m), descending through a canyon valley (510 m),
     * and crossing Mountain Pass B (968 m) into the next valley with switchback lean G-forces.
     */
    fun buildSampleOffroadCrossingTrack(endTimeMs: Long): TrackSessionDetail {
        val originLat = 40.042000
        val originLon = 115.962000
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(Math.toRadians(originLat))

        // Open point-to-point twin-mountain-pass trail in local meters (x = East, y = North)
        val passWaypoints = listOf(
            0.0 to 0.0,
            720.0 to 520.0,
            1540.0 to 920.0,
            1980.0 to 1640.0,
            2860.0 to 2180.0,
            3420.0 to 3080.0,
            4380.0 to 3420.0,
            5160.0 to 4280.0,
            6280.0 to 4680.0,
            7020.0 to 5540.0,
            8120.0 to 5960.0,
            8890.0 to 6920.0,
            9980.0 to 7380.0,
            10960.0 to 8240.0,
            12140.0 to 8920.0
        )

        val denseTrail = sampleOpenCatmullRom(passWaypoints, samplesPerSegment = 12)
        val n = denseTrail.size
        val totalDurationMs = 2_040_000L // 34 min
        val startTimeMs = endTimeMs - totalDurationMs

        val points = mutableListOf<TrackPointEntity>()

        var cursorMs = startTimeMs
        var totalDist = 0.0
        var maxSpeed = 0f
        var minAlt = Double.POSITIVE_INFINITY
        var maxAlt = Double.NEGATIVE_INFINITY
        var elevGain = 0.0
        var elevLoss = 0.0
        var prevCountedAlt: Double? = null
        var peakTotalG = 0f
        var peakLatG = 0f
        var hrSum = 0L
        var hrCount = 0
        var maxHr = 0

        // Twin mountain passes: valley 435m -> Pass A 845m (p=0.31) -> mid valley 520m (p=0.52)
        // -> Pass B 968m (p=0.77) -> far valley 480m
        fun altitudeAt(p: Double): Double {
            val base = 435.0 + 45.0 * p
            val peakA = 405.0 * kotlin.math.exp(-((p - 0.30) / 0.145) * ((p - 0.30) / 0.145))
            val peakB = 505.0 * kotlin.math.exp(-((p - 0.76) / 0.155) * ((p - 0.76) / 0.155))
            val micro = 14.0 * sin(14.0 * PI * p)
            return base + peakA + peakB + micro
        }

        val segLengths = DoubleArray(n - 1) { i ->
            val curr = denseTrail[i]
            val next = denseTrail[i + 1]
            hypot(next.first - curr.first, next.second - curr.second)
        }
        val alts = DoubleArray(n) { i -> altitudeAt(i.toDouble() / (n - 1).coerceAtLeast(1)) }

        val curvatures = DoubleArray(n)
        for (i in 1 until n - 1) {
            val prev = denseTrail[i - 1]
            val curr = denseTrail[i]
            val next = denseTrail[i + 1]
            val v1x = curr.first - prev.first
            val v1y = curr.second - prev.second
            val v2x = next.first - curr.first
            val v2y = next.second - curr.second
            val cross = v1x * v2y - v1y * v2x
            val dot = v1x * v2x + v1y * v2y
            val angle = atan2(cross, dot)
            val avgLen = (hypot(v1x, v1y) + hypot(v2x, v2y)).coerceAtLeast(1.0) * 0.5
            curvatures[i] = angle / avgLen
        }

        val rawDtSec = DoubleArray(n - 1) { i ->
            val slope = abs((alts[i + 1] - alts[i]) / segLengths[i].coerceAtLeast(1.0))
            val curveFactor = abs(curvatures[i]) * 260.0
            val motoSpeedMps = (14.2 / (1.0 + curveFactor * 0.45 + slope * 2.2)).coerceIn(5.8, 17.5)
            segLengths[i] / motoSpeedMps
        }
        val rawTotalSec = rawDtSec.sum().coerceAtLeast(1.0)
        val timeScale = (totalDurationMs / 1000.0) / rawTotalSec

        var prevSpeedMps = 8.5f
        for (i in 0 until n) {
            val (x, y) = denseTrail[i]
            val (nx, ny) = if (i < n - 1) denseTrail[i + 1] else denseTrail[i]
            val (px, py) = if (i > 0) denseTrail[i - 1] else denseTrail[i]
            val lat = originLat + (y / METERS_PER_DEG_LAT)
            val lon = originLon + (x / metersPerDegLon)
            val alt = alts[i]

            val dtSec = if (i < n - 1) (rawDtSec[i] * timeScale).coerceAtLeast(1.5) else 2.0
            val segLen = if (i < n - 1) segLengths[i] else segLengths.last()
            val speedMps = (segLen / dtSec).toFloat().coerceIn(5.2f, 18.8f)

            val dx = if (i < n - 1) nx - x else x - px
            val dy = if (i < n - 1) ny - y else y - py
            val bearing = ((Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0).toFloat()

            // Motorcycle corner lean lateral G + throttle/braking longitudinal G
            val rawLatG = ((speedMps * speedMps * curvatures[i]) / 9.80665 * 2.4).toFloat().coerceIn(-0.78f, 0.78f)
            val rawLongG = ((speedMps - prevSpeedMps) / dtSec.toFloat() / 9.80665f * 2.2f).coerceIn(-0.62f, 0.52f)
            val totalG = sqrt(rawLatG * rawLatG + rawLongG * rawLongG)
            prevSpeedMps = speedMps

            val grade = if (i < n - 1) (alts[i + 1] - alt) / segLen.coerceAtLeast(1.0) else 0.0
            val hr = (118 + (abs(rawLatG) * 32f).roundToInt() + (grade.coerceAtLeast(0.0) * 140.0).roundToInt() +
                (8.0 * sin(2.0 * PI * i / n)).roundToInt()).coerceIn(108, 164)

            if (alt < minAlt) minAlt = alt
            if (alt > maxAlt) maxAlt = alt
            val prevC = prevCountedAlt
            if (prevC == null) {
                prevCountedAlt = alt
            } else if (abs(alt - prevC) >= 2.5) {
                if (alt > prevC) elevGain += (alt - prevC) else elevLoss += (prevC - alt)
                prevCountedAlt = alt
            }

            points.add(
                TrackPointEntity(
                    sessionId = 0L,
                    timestampMs = cursorMs,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = alt,
                    speedMps = speedMps,
                    accuracyMeters = 3.2f,
                    bearingDegrees = bearing,
                    heartRateBpm = hr,
                    lapNumber = 0,
                    lateralG = rawLatG,
                    longitudinalG = rawLongG,
                    totalG = totalG
                )
            )

            if (i < n - 1) totalDist += segLen
            if (speedMps > maxSpeed) maxSpeed = speedMps
            if (totalG > peakTotalG) peakTotalG = totalG
            if (abs(rawLatG) > peakLatG) peakLatG = abs(rawLatG)
            hrSum += hr
            hrCount++
            if (hr > maxHr) maxHr = hr

            cursorMs += (dtSec * 1000.0).toLong()
        }

        val actualDurationMs = (cursorMs - startTimeMs).coerceAtLeast(1L)
        val avgSpeedMps = (totalDist / (actualDurationMs / 1000.0)).toFloat()

        val session = TrackSessionEntity(
            name = SAMPLE_OFFROAD_NAME,
            mode = ActivityMode.OFFROAD_CROSSING.name,
            startTimeMs = startTimeMs,
            endTimeMs = cursorMs,
            durationMs = actualDurationMs,
            distanceMeters = totalDist,
            maxSpeedMps = maxSpeed,
            avgSpeedMps = avgSpeedMps,
            elevationGainMeters = elevGain,
            elevationLossMeters = elevLoss,
            minAltitudeMeters = minAlt.takeIf { it.isFinite() },
            maxAltitudeMeters = maxAlt.takeIf { it.isFinite() },
            lapCount = 0,
            bestLapMs = null,
            avgHeartRate = if (hrCount > 0) (hrSum / hrCount).toInt() else null,
            maxHeartRate = maxHr.takeIf { it > 0 },
            pointCount = points.size,
            peakTotalG = peakTotalG,
            peakLateralG = peakLatG
        )
        return TrackSessionDetail(session = session, points = points, laps = emptyList())
    }

    private fun sampleOpenCatmullRom(
        controlPoints: List<Pair<Double, Double>>,
        samplesPerSegment: Int
    ): List<Pair<Double, Double>> {
        val n = controlPoints.size
        if (n < 2) return controlPoints
        val result = ArrayList<Pair<Double, Double>>((n - 1) * samplesPerSegment + 1)
        for (i in 0 until n - 1) {
            val p0 = controlPoints[(i - 1).coerceAtLeast(0)]
            val p1 = controlPoints[i]
            val p2 = controlPoints[i + 1]
            val p3 = controlPoints[(i + 2).coerceAtMost(n - 1)]
            for (step in 0 until samplesPerSegment) {
                val t = step.toDouble() / samplesPerSegment
                val t2 = t * t
                val t3 = t2 * t
                val x = 0.5 * (
                    (2.0 * p1.first) +
                        (-p0.first + p2.first) * t +
                        (2.0 * p0.first - 5.0 * p1.first + 4.0 * p2.first - p3.first) * t2 +
                        (-p0.first + 3.0 * p1.first - 3.0 * p2.first + p3.first) * t3
                    )
                val y = 0.5 * (
                    (2.0 * p1.second) +
                        (-p0.second + p2.second) * t +
                        (2.0 * p0.second - 5.0 * p1.second + 4.0 * p2.second - p3.second) * t2 +
                        (-p0.second + 3.0 * p1.second - 3.0 * p2.second + p3.second) * t3
                    )
                result.add(x to y)
            }
        }
        result.add(controlPoints.last())
        return result
    }

    private fun sampleClosedCatmullRom(
        controlPoints: List<Pair<Double, Double>>,
        samplesPerSegment: Int
    ): List<Pair<Double, Double>> {
        val n = controlPoints.size
        if (n < 3) return controlPoints
        val result = ArrayList<Pair<Double, Double>>(n * samplesPerSegment)
        for (i in 0 until n) {
            val p0 = controlPoints[(i - 1 + n) % n]
            val p1 = controlPoints[i]
            val p2 = controlPoints[(i + 1) % n]
            val p3 = controlPoints[(i + 2) % n]
            for (step in 0 until samplesPerSegment) {
                val t = step.toDouble() / samplesPerSegment
                val t2 = t * t
                val t3 = t2 * t
                val x = 0.5 * (
                    (2.0 * p1.first) +
                        (-p0.first + p2.first) * t +
                        (2.0 * p0.first - 5.0 * p1.first + 4.0 * p2.first - p3.first) * t2 +
                        (-p0.first + 3.0 * p1.first - 3.0 * p2.first + p3.first) * t3
                    )
                val y = 0.5 * (
                    (2.0 * p1.second) +
                        (-p0.second + p2.second) * t +
                        (2.0 * p0.second - 5.0 * p1.second + 4.0 * p2.second - p3.second) * t2 +
                        (-p0.second + 3.0 * p1.second - 3.0 * p2.second + p3.second) * t3
                    )
                result.add(x to y)
            }
        }
        return result
    }
}
