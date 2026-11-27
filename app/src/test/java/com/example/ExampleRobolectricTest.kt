package com.example

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.view.Surface
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.alert.SafetyAlertMonitor
import com.example.ble.BleConnectionStatus
import com.example.ble.HeartRateState
import com.example.ble.parseHeartRateBpm
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.GpsRepository
import com.example.gps.OverlayScale
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.overlay.OverlayWindowController
import com.example.sensor.GForceFusionEngine
import com.example.track.ActivityMode
import com.example.track.GpxExporter
import com.example.track.TelemetryJsonExporter
import com.example.track.TrackFormatters
import com.example.track.TrackLapEntity
import com.example.track.TrackPointEntity
import com.example.track.TrackRecorder
import com.example.track.TrackSessionEntity
import com.example.ui.GpsDashboardViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.sqrt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GPS 悬浮仪表", appName)
    }

    @Test
    fun `empty gps data formats gracefully with dashes`() {
        val empty = GpsData()
        assertEquals("--", empty.formattedSpeedValue(SpeedUnit.KMH))
        assertEquals("--", empty.formattedAltitude())
        assertEquals("--", empty.formattedAccuracy())
        assertEquals("--", empty.formattedBearing())
        assertEquals("--", empty.formattedSatellites())
        assertNull(empty.formattedCoordinates())
    }

    @Test
    fun `locked gps data formats speed coordinates and Chinese compass bearing`() {
        val data = GpsData(
            hasFix = true,
            latitude = 39.9042,
            longitude = 116.407396,
            speedMps = 20f,
            altitudeMeters = 45.24,
            accuracyMeters = 3.5f,
            bearingDegrees = 128f,
            satellitesUsed = 12,
            satellitesVisible = 24,
            hasSatelliteInfo = true,
            fixStatus = GpsFixStatus.LOCKED
        )
        assertEquals("72.0", data.formattedSpeedValue(SpeedUnit.KMH))
        assertEquals("44.7", data.formattedSpeedValue(SpeedUnit.MPH))
        assertEquals("39.904200, 116.407396", data.formattedCoordinates())
        assertEquals("45.2 m", data.formattedAltitude())
        assertEquals("±3.5 m", data.formattedAccuracy())
        assertEquals("128° 东南", data.formattedBearing())
        assertEquals("12 / 24 颗", data.formattedSatellites())
    }

    @Test
    fun `parseHeartRateBpm decodes standard 8-bit and 16-bit GATT 0x2A37 packets`() {
        // 8-bit heart rate: flags=0x00, bpm=148 (0x94)
        assertEquals(148, parseHeartRateBpm(byteArrayOf(0x00, 148.toByte())))
        // 16-bit heart rate: flags=0x01, bpm=172 (0xAC, 0x00)
        assertEquals(172, parseHeartRateBpm(byteArrayOf(0x01, 172.toByte(), 0x00)))
        // Out of physiological range or empty returns null
        assertNull(parseHeartRateBpm(byteArrayOf()))
        assertNull(parseHeartRateBpm(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `track formatters and gpx export produce valid lap times and HR XML`() {
        assertEquals("48.32s", TrackFormatters.formatLapTime(48_320L))
        assertEquals("1:05.12", TrackFormatters.formatLapTime(65_120L))
        assertEquals("最佳", TrackFormatters.formatLapDelta(48_320L, 48_320L))
        assertEquals("+0.55s", TrackFormatters.formatLapDelta(48_870L, 48_320L))

        val dist = TrackRecorder.haversineMeters(39.904200, 116.407396, 39.905200, 116.407396)
        assertTrue(dist in 105.0..118.0)

        val session = TrackSessionEntity(
            id = 1L,
            name = "卡丁车 · 测试",
            mode = ActivityMode.KARTING.name,
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_000_120_000L,
            durationMs = 120_000L,
            distanceMeters = 1250.0,
            maxSpeedMps = 18.5f,
            avgSpeedMps = 10.4f,
            elevationGainMeters = 12.0,
            elevationLossMeters = 12.0,
            minAltitudeMeters = 40.0,
            maxAltitudeMeters = 52.0,
            lapCount = 2,
            bestLapMs = 54_200L,
            avgHeartRate = 152,
            maxHeartRate = 168,
            pointCount = 1
        )
        val point = TrackPointEntity(
            sessionId = 1L,
            timestampMs = 1_700_000_005_000L,
            latitude = 39.904200,
            longitude = 116.407396,
            altitudeMeters = 45.2,
            speedMps = 16.5f,
            accuracyMeters = 3.0f,
            bearingDegrees = 90f,
            heartRateBpm = 156,
            lapNumber = 1
        )
        val xml = GpxExporter.buildGpxXml(session, listOf(point))
        assertTrue(xml.contains("<ele>45.2</ele>"))
        assertTrue(xml.contains("<gpxtpx:hr>156</gpxtpx:hr>"))
        assertTrue(xml.contains("""lat="39.904200" lon="116.407396""""))
    }

    @Test
    fun `structured telemetry json exporter includes HR zones, G-force peaks, laps, and fused samples`() {
        val session = TrackSessionEntity(
            id = 42L,
            name = "卡丁车 · 赛道测试",
            mode = ActivityMode.KARTING.name,
            startTimeMs = 1_700_000_000_000L,
            endTimeMs = 1_700_000_060_000L,
            durationMs = 60_000L,
            distanceMeters = 980.0,
            maxSpeedMps = 20.0f,
            avgSpeedMps = 16.3f,
            elevationGainMeters = 4.0,
            elevationLossMeters = 4.0,
            minAltitudeMeters = 41.0,
            maxAltitudeMeters = 45.0,
            lapCount = 1,
            bestLapMs = 52_400L,
            avgHeartRate = 158,
            maxHeartRate = 172,
            peakTotalG = 1.45f,
            peakLateralG = 1.38f,
            pointCount = 2
        )
        val p1 = TrackPointEntity(
            sessionId = 42L,
            timestampMs = 1_700_000_000_000L,
            latitude = 39.904200,
            longitude = 116.407396,
            altitudeMeters = 42.0,
            speedMps = 15.0f,
            accuracyMeters = 2.5f,
            bearingDegrees = 45f,
            heartRateBpm = 150,
            lateralG = 0.85f,
            longitudinalG = -0.42f,
            totalG = 0.95f,
            lapNumber = 1
        )
        val p2 = TrackPointEntity(
            sessionId = 42L,
            timestampMs = 1_700_000_002_000L,
            latitude = 39.904400,
            longitude = 116.407500,
            altitudeMeters = 42.5,
            speedMps = 20.0f,
            accuracyMeters = 2.2f,
            bearingDegrees = 50f,
            heartRateBpm = 168,
            lateralG = 1.38f,
            longitudinalG = -0.44f,
            totalG = 1.45f,
            lapNumber = 1
        )
        val lap = TrackLapEntity(
            sessionId = 42L,
            lapNumber = 1,
            lapDurationMs = 52_400L,
            lapDistanceMeters = 980.0,
            maxSpeedMps = 20.0f,
            avgHeartRate = 159,
            peakG = 1.45f
        )

        val jsonText = TelemetryJsonExporter.buildStructuredJson(session, listOf(p1, p2), listOf(lap))
        val root = JSONObject(jsonText)
        assertEquals("1.0", root.getString("schema_version"))
        assertEquals("KARTING", root.getString("activity_type"))

        val sessionObj = root.getJSONObject("session")
        val hrObj = sessionObj.getJSONObject("heart_rate_bpm")
        assertEquals(2, hrObj.getInt("connected_samples"))
        assertEquals(158, hrObj.getInt("avg"))
        assertTrue(hrObj.has("zones_sec"))
        assertEquals(2.0, hrObj.getJSONObject("zones_sec").getDouble("z4_155_174"), 0.01)

        val gObj = sessionObj.getJSONObject("g_force")
        assertEquals(1.45, gObj.getDouble("peak_total_g"), 0.01)
        assertEquals(1.38, gObj.getDouble("peak_lateral_g"), 0.01)
        assertEquals(0.44, gObj.getDouble("peak_braking_g"), 0.01)

        val lapsArr = root.getJSONArray("laps")
        assertEquals(1, lapsArr.length())
        assertEquals(1.45, lapsArr.getJSONObject(0).getDouble("peak_g"), 0.01)
        assertEquals(159, lapsArr.getJSONObject(0).getInt("avg_heart_rate_bpm"))

        val samplesArr = root.getJSONArray("samples")
        assertEquals(2, samplesArr.length())
        assertEquals(168, samplesArr.getJSONObject(1).getInt("hr_bpm"))
        assertEquals(1.38, samplesArr.getJSONObject(1).getDouble("lat_g"), 0.01)
        assertEquals(1.45, samplesArr.getJSONObject(1).getDouble("total_g"), 0.01)
    }

    @Test
    fun `motion sensor fusion cancels gravity at tilted mount and computes accurate gyro-fused G-force`() {
        val engine = GForceFusionEngine()
        val g = GForceFusionEngine.STANDARD_GRAVITY
        val invSqrt2 = (1.0 / sqrt(2.0)).toFloat()

        // 1. Stationary phone mounted at 45° tilt on handlebar: gravity split equally across +Y and +Z
        var state = engine.onAccelerometerSample(
            ax = 0f,
            ay = g * invSqrt2,
            az = g * invSqrt2,
            elapsedRealtimeMs = 100L,
            hasGyroscope = true
        )
        assertEquals(0f, state.lateralG, 0.01f)
        assertEquals(0f, state.longitudinalG, 0.01f)
        assertEquals(0f, state.totalG, 0.01f)
        assertFalse(state.isGyroFused)

        // 2. Feed gyroscope readings during a steady turn (1.0 rad/s around vertical gravity axis)
        engine.onGyroscopeSample(wx = 0f, wy = 1f * invSqrt2, wz = 1f * invSqrt2, timestampNs = 1_000_000_000L)
        engine.onGyroscopeSample(wx = 0f, wy = 1f * invSqrt2, wz = 1f * invSqrt2, timestampNs = 1_020_000_000L)

        // 3. Apply pure 1.0G lateral cornering force (+X) while tilted 45° for several samples to settle smoothing
        var tMs = 120L
        var tNs = 1_040_000_000L
        repeat(25) {
            engine.onGyroscopeSample(wx = 0f, wy = 1f * invSqrt2, wz = 1f * invSqrt2, timestampNs = tNs)
            state = engine.onAccelerometerSample(
                ax = 1.0f * g,
                ay = g * invSqrt2,
                az = g * invSqrt2,
                elapsedRealtimeMs = tMs,
                hasGyroscope = true
            )
            tMs += 20L
            tNs += 20_000_000L
        }

        assertTrue(state.isGyroFused)
        assertTrue("Expected yaw rate ~ -57 deg/s, got ${state.yawRateDegPerSec}", state.yawRateDegPerSec < -50f)
        // Gyro trust keeps gravity reference stable during high-G cornering so lateralG stays close to 1.0G
        assertTrue("Expected lateralG > 0.85G, got ${state.lateralG}", state.lateralG > 0.85f)
        assertTrue("Expected longitudinalG near 0, got ${state.longitudinalG}", kotlin.math.abs(state.longitudinalG) < 0.15f)
        assertTrue("Expected peakG >= totalG", state.peakG >= state.totalG)

        // 4. Zero calibration resets residual reading & peak cleanly
        engine.calibrateZero()
        state = engine.onAccelerometerSample(
            ax = 0f,
            ay = g * invSqrt2,
            az = g * invSqrt2,
            elapsedRealtimeMs = tMs + 50L,
            hasGyroscope = true
        )
        engine.calibrateZero()
        state = engine.onAccelerometerSample(
            ax = 0f,
            ay = g * invSqrt2,
            az = g * invSqrt2,
            elapsedRealtimeMs = tMs + 70L,
            hasGyroscope = true
        )
        assertEquals(0f, state.totalG, 0.02f)
    }

    @Test
    fun `dashboard viewModel exposes live GForceData stateflow and calibration controls`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GpsDashboardViewModel(app)
        vm.startMotionSensors()
        vm.calibrateGForceZero()
        vm.resetGForcePeak()
        assertEquals(0f, vm.gForceData.value.totalG, 0.001f)
        assertEquals(0f, vm.gForceData.value.peakG, 0.001f)
        vm.stopMotionSensors()
    }

    @Test
    fun `overlay window layout params configure always-on-top non-focusable application overlay`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val params = OverlayWindowController.createOverlayLayoutParams(context)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
        assertTrue((params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
    }

    @Test
    fun `overlay display config supports toggling speed and guards against disabling all items`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GpsDashboardViewModel(app)
        vm.resetOverlayConfig()

        // Default config enables speed, G-Force, and altitude
        assertTrue(vm.overlayConfig.value.showSpeed)
        assertTrue(vm.overlayConfig.value.showGForce)
        assertTrue(vm.overlayConfig.value.showAltitude)

        // Turning off altitude and G-Force succeeds while speed stays enabled
        assertTrue(vm.updateOverlayConfig { it.copy(showAltitude = false, showGForce = false) })
        assertEquals(1, vm.overlayConfig.value.enabledCount)

        // Attempting to turn off the last remaining item (speed) is rejected
        assertFalse(vm.updateOverlayConfig { it.copy(showSpeed = false) })
        assertTrue(vm.overlayConfig.value.showSpeed)

        // Reset restores defaults cleanly
        vm.resetOverlayConfig()
        assertEquals(3, vm.overlayConfig.value.enabledCount)
    }

    @Test
    fun `gforce peak holds vector direction and expires according to configurable peakHoldDurationMs`() {
        val engine = GForceFusionEngine()
        val g = GForceFusionEngine.STANDARD_GRAVITY

        // Configure 60-second peak hold window
        engine.setPeakHoldDurationMs(60_000L)

        // Initialize flat phone with gyro active at t = 1000ms
        engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = 1_000L, hasGyroscope = true)
        engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = 1_000_000_000L)

        // Apply sustained +0.9G lateral cornering force for 15 samples (300ms)
        var state = engine.onAccelerometerSample(0.9f * g, 0f, g, elapsedRealtimeMs = 1_020L, hasGyroscope = true)
        repeat(15) { i ->
            val tMs = 1_040L + i * 20L
            engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state = engine.onAccelerometerSample(0.9f * g, 0f, g, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        val recordedPeak = state.peakG
        assertTrue("Expected peakG > 0.70G, got $recordedPeak", recordedPeak > 0.70f)
        assertTrue("Expected positive peakLateralG, got ${state.peakLateralG}", state.peakLateralG > 0.70f)

        // Return to stationary and check at 30s later -> peak MUST still be preserved
        repeat(15) { i ->
            state = engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = 31_000L + i * 20L, hasGyroscope = true)
        }
        assertEquals(recordedPeak, state.peakG, 0.01f)
        assertTrue(state.peakLateralG > 0.70f)

        // Stationary 65s after peak -> peak MUST be flushed cleanly to ~0
        state = engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = 70_000L, hasGyroscope = true)
        assertTrue("Expected peak to refresh after 60s hold, got ${state.peakG}", state.peakG < 0.05f)

        // Infinite hold mode (0L) preserves peak indefinitely until manual reset
        engine.setPeakHoldDurationMs(0L)
        repeat(15) { i ->
            val tMs = 71_000L + i * 20L
            engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state = engine.onAccelerometerSample(0f, 0.85f * g, g, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        val foreverPeak = state.peakG
        assertTrue("Expected foreverPeak > 0.65G, got $foreverPeak", foreverPeak > 0.65f)
        repeat(10) { i ->
            state = engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = 700_000L + i * 20L, hasGyroscope = true)
        }
        assertEquals(foreverPeak, state.peakG, 0.01f)
    }

    @Test
    fun `safety alert monitor detects overspeed and high heart rate thresholds accurately`() {
        val config = SafetyAlertConfig(
            speedAlertEnabled = true,
            speedLimitKmh = 60,
            hrAlertEnabled = true,
            hrLimitBpm = 175,
            vibrateOnAlert = true
        )

        val safeGps = GpsData(hasFix = true, speedMps = 12f) // 43.2 km/h
        val fastGps = GpsData(hasFix = true, speedMps = 18f) // 64.8 km/h
        val safeHr = HeartRateState(status = BleConnectionStatus.CONNECTED, bpm = 152)
        val highHr = HeartRateState(status = BleConnectionStatus.CONNECTED, bpm = 181)

        val safeState = SafetyAlertMonitor.evaluate(safeGps, safeHr, config)
        assertFalse(safeState.isOverspeed)
        assertFalse(safeState.isHighHeartRate)
        assertFalse(safeState.isAnyAlert)

        val speedAlert = SafetyAlertMonitor.evaluate(fastGps, safeHr, config)
        assertTrue(speedAlert.isOverspeed)
        assertFalse(speedAlert.isHighHeartRate)
        assertTrue(speedAlert.isAnyAlert)

        val hrAlert = SafetyAlertMonitor.evaluate(safeGps, highHr, config)
        assertFalse(hrAlert.isOverspeed)
        assertTrue(hrAlert.isHighHeartRate)
        assertTrue(hrAlert.isAnyAlert)

        val disabledAlert = SafetyAlertMonitor.evaluate(
            fastGps,
            highHr,
            config.copy(speedAlertEnabled = false, hrAlertEnabled = false)
        )
        assertFalse(disabledAlert.isAnyAlert)
    }

    @Test
    fun `overlay appearance font scale position memory and safety alerts persist cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GpsDashboardViewModel(app)

        vm.updateOverlayConfig {
            it.copy(
                backgroundAlpha = 0.72f,
                scale = OverlayScale.LARGE,
                fontScale = 1.40f,
                showGForcePeakOnDial = true,
                gForcePeakHoldSeconds = 180
            )
        }
        assertEquals(0.72f, vm.overlayConfig.value.backgroundAlpha, 0.005f)
        assertEquals(OverlayScale.LARGE, vm.overlayConfig.value.scale)
        assertEquals(1.40f, vm.overlayConfig.value.fontScale, 0.005f)
        assertEquals(180, vm.overlayConfig.value.gForcePeakHoldSeconds)

        GpsRepository.saveOverlayPosition(app, x = 240, y = 410)
        assertEquals(240 to 410, GpsRepository.loadOverlayPosition(app))

        vm.updateSafetyAlertConfig {
            it.copy(speedAlertEnabled = true, speedLimitKmh = 95, hrAlertEnabled = true, hrLimitBpm = 180)
        }
        assertTrue(vm.safetyAlertConfig.value.speedAlertEnabled)
        assertEquals(95, vm.safetyAlertConfig.value.speedLimitKmh)
        assertEquals(180, vm.safetyAlertConfig.value.hrLimitBpm)
    }

    @Test
    fun `landscape ROTATION_90 and ROTATION_270 remap lateral and longitudinal G-force accurately for motorcycle mounts`() {
        val g = GForceFusionEngine.STANDARD_GRAVITY
        val invSqrt2 = (1.0 / sqrt(2.0)).toFloat()

        // --- Case 1: Surface.ROTATION_90 (phone turned 90° CCW; top points LEFT, bottom/USB points RIGHT) ---
        val engine90 = GForceFusionEngine()
        engine90.setDisplayRotation(Surface.ROTATION_90)

        // Tilted 45° back on motorcycle handlebar: gravity points along (+X_dev + Z_dev)/sqrt(2)
        engine90.onAccelerometerSample(g * invSqrt2, 0f, g * invSqrt2, elapsedRealtimeMs = 100L, hasGyroscope = true)
        engine90.onGyroscopeSample(0f, 0f, 0f, timestampNs = 100_000_000L)

        // Apply +1.0G force to Screen-RIGHT (which is -Y_dev in ROTATION_90!)
        var state90 = engine90.onAccelerometerSample(g * invSqrt2, -1.0f * g, g * invSqrt2, elapsedRealtimeMs = 120L, hasGyroscope = true)
        repeat(20) { i ->
            val tMs = 140L + i * 20L
            engine90.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state90 = engine90.onAccelerometerSample(g * invSqrt2, -1.0f * g, g * invSqrt2, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        assertTrue("Expected landscape flag true", state90.isLandscape)
        assertTrue("ROTATION_90: rightward force (-Y_dev) must map to +lateralG > 0.85, got ${state90.lateralG}", state90.lateralG > 0.85f)
        assertTrue("ROTATION_90: longitudinalG should be near 0, got ${state90.longitudinalG}", kotlin.math.abs(state90.longitudinalG) < 0.15f)

        // Now apply +1.0G forward acceleration (ahead of bike = (+X_dev - Z_dev)/sqrt(2) in 45° tilted ROTATION_90)
        repeat(25) { i ->
            val tMs = 600L + i * 20L
            engine90.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state90 = engine90.onAccelerometerSample(
                ax = g * invSqrt2 + g * invSqrt2,
                ay = 0f,
                az = g * invSqrt2 - g * invSqrt2,
                elapsedRealtimeMs = tMs,
                hasGyroscope = true
            )
        }
        assertTrue("ROTATION_90: forward force must map to +longitudinalG > 0.85, got ${state90.longitudinalG}", state90.longitudinalG > 0.85f)
        assertTrue("ROTATION_90: lateralG should be near 0, got ${state90.lateralG}", kotlin.math.abs(state90.lateralG) < 0.15f)

        // --- Case 2: Surface.ROTATION_270 (phone turned 90° CW; top points RIGHT, bottom/USB points LEFT) ---
        val engine270 = GForceFusionEngine()
        engine270.setDisplayRotation(Surface.ROTATION_270)

        // Tilted 45° back on handlebar: gravity points along (-X_dev + Z_dev)/sqrt(2)
        engine270.onAccelerometerSample(-g * invSqrt2, 0f, g * invSqrt2, elapsedRealtimeMs = 100L, hasGyroscope = true)
        engine270.onGyroscopeSample(0f, 0f, 0f, timestampNs = 100_000_000L)

        // Apply +1.0G force to Screen-RIGHT (which is +Y_dev in ROTATION_270!)
        var state270 = engine270.onAccelerometerSample(-g * invSqrt2, 1.0f * g, g * invSqrt2, elapsedRealtimeMs = 120L, hasGyroscope = true)
        repeat(20) { i ->
            val tMs = 140L + i * 20L
            engine270.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state270 = engine270.onAccelerometerSample(-g * invSqrt2, 1.0f * g, g * invSqrt2, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        assertTrue(state270.isLandscape)
        assertTrue("ROTATION_270: rightward force (+Y_dev) must map to +lateralG > 0.85, got ${state270.lateralG}", state270.lateralG > 0.85f)
        assertTrue("ROTATION_270: longitudinalG should be near 0, got ${state270.longitudinalG}", kotlin.math.abs(state270.longitudinalG) < 0.15f)
    }

    @Test
    fun `overlay position memory stores portrait and landscape coordinates independently`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        GpsRepository.saveOverlayPosition(app, x = 80, y = 1400, isLandscape = false)
        GpsRepository.saveOverlayPosition(app, x = 1850, y = 40, isLandscape = true)

        assertEquals(80 to 1400, GpsRepository.loadOverlayPosition(app, isLandscape = false))
        assertEquals(1850 to 40, GpsRepository.loadOverlayPosition(app, isLandscape = true))
    }
}
