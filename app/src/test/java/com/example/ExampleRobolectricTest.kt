package com.example

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.location.GnssStatus
import android.view.Surface
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.alert.SafetyAlertMonitor
import com.example.ble.BleConnectionStatus
import com.example.ble.BleDeviceCategory
import com.example.ble.BleDeviceClassifier
import com.example.ble.BleDeviceSource
import com.example.ble.BleHrDevice
import com.example.ble.HeartRateState
import com.example.ble.parseHeartRateBpm
import com.example.gps.AltitudeSessionTracker
import com.example.gps.GnssConstellation
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.GpsRepository
import com.example.gps.OverlayScale
import com.example.gps.SafetyAlertConfig
import com.example.gps.SpeedUnit
import com.example.overlay.OverlayWindowController
import com.example.sensor.CompassMath
import com.example.sensor.GForceFusionEngine
import com.example.track.ActivityMode
import com.example.track.GpxExporter
import com.example.track.OffroadAnalysisEngine
import com.example.track.SampleTrackSeeder
import com.example.track.SocialBgPreset
import com.example.track.SocialCardBitmapRenderer
import com.example.track.SocialCardConfig
import com.example.track.TelemetryJsonExporter
import com.example.track.TrackFormatters
import com.example.track.TrackLapEntity
import com.example.track.TrackPointEntity
import com.example.track.TrackRecorder
import com.example.track.TrackSessionEntity
import com.example.track.TrackTelemetryAnalyzer
import com.example.ui.GpsDashboardViewModel
import com.example.ui.findNearestPointIndexByTimeFraction
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
        assertEquals("45 m", data.formattedAltitude())
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

    @Test
    fun `gforce engine records 36-sector directional max envelope shape and expires sectors independently`() {
        val engine = GForceFusionEngine()
        val g = GForceFusionEngine.STANDARD_GRAVITY
        engine.setPeakHoldDurationMs(60_000L)

        // Flat phone initialized at t = 1_000ms
        engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = 1_000L, hasGyroscope = true)
        engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = 1_000_000_000L)

        // 1. Right turn (+1.0G lateral, sector 0 = 0°) at t ~ 1.4s
        var state = engine.onAccelerometerSample(g, 0f, g, elapsedRealtimeMs = 1_020L, hasGyroscope = true)
        repeat(20) { i ->
            val tMs = 1_040L + i * 20L
            engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state = engine.onAccelerometerSample(g, 0f, g, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        assertEquals(GForceFusionEngine.PEAK_ENVELOPE_SECTORS, state.peakEnvelope.size)
        assertTrue("Expected right sector (0) > 0.80G, got ${state.peakEnvelope[0]}", state.peakEnvelope[0] > 0.80f)

        // Return to neutral before braking
        repeat(20) { i ->
            val tMs = 2_000L + i * 20L
            engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state = engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }

        // 2. Hard braking (-0.8G longitudinal => -Y_dev on flat phone, sector 9 = 90°) at t = 35s
        repeat(20) { i ->
            val tMs = 35_000L + i * 20L
            engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state = engine.onAccelerometerSample(0f, -0.8f * g, g, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        // Both right turn (sector 0) AND braking (sector 9) are simultaneously recorded in the envelope!
        assertTrue("Right turn lobe preserved: ${state.peakEnvelope[0]}", state.peakEnvelope[0] > 0.80f)
        assertTrue("Braking lobe recorded at sector 9: ${state.peakEnvelope[9]}", state.peakEnvelope[9] > 0.65f)
        assertTrue("Untouched accel sector (27) stays near 0, got ${state.peakEnvelope[27]}", state.peakEnvelope[27] < 0.05f)
        assertTrue("Opposite left sector (18) stays minimal, got ${state.peakEnvelope[18]}", state.peakEnvelope[18] < 0.20f)

        // 3. At t = 70s (>60s after right turn, only 35s after braking):
        //    right-turn sector expires to 0, while braking lobe stays intact and becomes active peakG!
        repeat(15) { i ->
            val tMs = 70_000L + i * 20L
            engine.onGyroscopeSample(0f, 0f, 0f, timestampNs = tMs * 1_000_000L)
            state = engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = tMs, hasGyroscope = true)
        }
        assertEquals("Expired right sector clears cleanly", 0f, state.peakEnvelope[0], 0.001f)
        assertTrue("Recent braking sector remains recorded", state.peakEnvelope[9] > 0.65f)
        assertTrue("Overall peakG falls back to remaining braking lobe", state.peakG > 0.65f)

        // 4. Manual resetPeak clears entire recorded envelope lobes cleanly
        engine.resetPeak()
        state = engine.onAccelerometerSample(0f, 0f, g, elapsedRealtimeMs = 71_000L, hasGyroscope = true)
        assertTrue(
            "Braking lobe should be cleared after resetPeak",
            (state.peakEnvelope.getOrNull(9) ?: 0f) < 0.05f
        )
    }

    @Test
    fun `altitude session tracker filters jitter deadband and computes climb descent grade and reset`() {
        val tracker = AltitudeSessionTracker()

        // Initial point at 100.0m
        var stats = tracker.onAltitudeSample(
            altitudeM = 100.0,
            verticalAccuracyM = 3.2f,
            horizontalSpeedMps = 5.0f,
            elapsedMs = 1_000L
        )
        assertEquals(100.0, stats.minAltitudeM!!, 0.01)
        assertEquals(100.0, stats.maxAltitudeM!!, 0.01)
        assertEquals(0.0, stats.elevationGainM, 0.01)

        // Small GPS jitter (+0.8m < 1.5m deadband) should not inflate gain
        stats = tracker.onAltitudeSample(
            altitudeM = 100.8,
            verticalAccuracyM = 3.0f,
            horizontalSpeedMps = 5.0f,
            elapsedMs = 3_000L
        )
        assertEquals(0.0, stats.elevationGainM, 0.01)

        // Real climb to 112.0m (+12.0m over 4 seconds = 3 m/s vertical, grade > 0)
        stats = tracker.onAltitudeSample(
            altitudeM = 112.0,
            verticalAccuracyM = 2.8f,
            horizontalSpeedMps = 10.0f,
            elapsedMs = 7_000L
        )
        assertEquals(12.0, stats.elevationGainM, 0.05)
        assertEquals(0.0, stats.elevationLossM, 0.05)
        assertEquals(112.0, stats.maxAltitudeM!!, 0.01)
        assertTrue("Expected positive vertical speed, got ${stats.verticalSpeedMps}", stats.verticalSpeedMps > 0.5f)
        assertTrue("Expected positive climb grade %, got ${stats.gradePercent}", (stats.gradePercent ?: 0f) > 5f)

        // Real descent to 105.0m (-7.0m)
        stats = tracker.onAltitudeSample(
            altitudeM = 105.0,
            verticalAccuracyM = 2.8f,
            horizontalSpeedMps = 10.0f,
            elapsedMs = 11_000L
        )
        assertEquals(12.0, stats.elevationGainM, 0.05)
        assertEquals(7.0, stats.elevationLossM, 0.05)
        assertEquals(100.0, stats.minAltitudeM!!, 0.01)
        assertEquals(4, stats.samples.size)

        // Reset clears cumulative stats and sample curve cleanly
        tracker.reset()
        stats = tracker.onAltitudeSample(
            altitudeM = 200.0,
            verticalAccuracyM = 2.0f,
            horizontalSpeedMps = 0f,
            elapsedMs = 15_000L
        )
        assertEquals(200.0, stats.minAltitudeM!!, 0.01)
        assertEquals(200.0, stats.maxAltitudeM!!, 0.01)
        assertEquals(0.0, stats.elevationGainM, 0.01)
        assertEquals(0.0, stats.elevationLossM, 0.01)
        assertEquals(1, stats.samples.size)
        assertNull(stats.gradePercent)
    }

    @Test
    fun `compass math handles 360 wrap around smoothing barometer QNH calibration and constellations`() {
        // Wrap-around shortest angular delta across North (355° -> 5° is +10°, not -350°)
        assertEquals(10f, CompassMath.shortestAngleDelta(355f, 5f), 0.001f)
        assertEquals(-10f, CompassMath.shortestAngleDelta(5f, 355f), 0.001f)
        assertEquals(0f, CompassMath.smoothAzimuth(355f, 5f, alpha = 0.5f), 0.001f)
        assertEquals("北", CompassMath.compassCardinalLabelZh(359f))
        assertEquals("东南", CompassMath.compassCardinalLabelZh(135f))
        assertEquals("西", CompassMath.compassCardinalLabelZh(270f))

        // Barometer QNH calibration round-trip: calibrating at known 520m reproduces 520m
        val measuredHpa = 955.0f
        val qnh = CompassMath.calibrateSeaLevelPressureHpa(measuredHpa, knownAltitudeMeters = 520.0)
        val recalcAlt = CompassMath.pressureToAltitudeMeters(qnh, measuredHpa)
        assertEquals(520.0f, recalcAlt, 0.5f)

        // GNSS constellation mapping
        assertEquals(GnssConstellation.BEIDOU, GnssConstellation.fromGnssStatusType(GnssStatus.CONSTELLATION_BEIDOU))
        assertEquals(GnssConstellation.GPS, GnssConstellation.fromGnssStatusType(GnssStatus.CONSTELLATION_GPS))
        assertEquals(GnssConstellation.GLONASS, GnssConstellation.fromGnssStatusType(GnssStatus.CONSTELLATION_GLONASS))
        assertEquals(GnssConstellation.GALILEO, GnssConstellation.fromGnssStatusType(GnssStatus.CONSTELLATION_GALILEO))
    }

    @Test
    fun `ble device classifier formats human readable labels and prioritizes local connected wearables`() {
        assertEquals(
            BleDeviceCategory.WEARABLE,
            BleDeviceClassifier.classifyCategory("Xiaomi Smart Band 9", advertisesHeartRate = false)
        )
        assertEquals(
            BleDeviceCategory.HEART_RATE_SENSOR,
            BleDeviceClassifier.classifyCategory("Polar H10", advertisesHeartRate = false)
        )
        assertEquals(
            BleDeviceCategory.HEART_RATE_SENSOR,
            BleDeviceClassifier.classifyCategory("Unknown Device", advertisesHeartRate = true)
        )
        assertEquals("就在手边 · 信号极强", BleDeviceClassifier.signalDescriptionZh(-50, BleDeviceSource.NEARBY_SCAN))
        assertEquals("系统已直连 · 就在身边", BleDeviceClassifier.signalDescriptionZh(null, BleDeviceSource.CONNECTED_LOCAL))
        assertEquals("尾号 2B:0C", BleDeviceClassifier.shortAddressTail("D4:8A:39:1F:2B:0C"))
        assertEquals(4, BleDeviceClassifier.signalBars(-52))
        assertEquals(1, BleDeviceClassifier.signalBars(-90))

        val tv = BleHrDevice(
            name = "Living Room TV",
            address = "AA:BB:CC:DD:EE:01",
            rssi = -45,
            source = BleDeviceSource.NEARBY_SCAN,
            category = BleDeviceCategory.OTHER
        )
        val localBand = BleHrDevice(
            name = "Xiaomi Smart Band 9",
            address = "AA:BB:CC:DD:EE:02",
            rssi = null,
            source = BleDeviceSource.CONNECTED_LOCAL,
            category = BleDeviceCategory.WEARABLE
        )
        val lastBand = BleHrDevice(
            name = "我的小米手环",
            address = "AA:BB:CC:DD:EE:03",
            rssi = -65,
            advertisesHeartRate = true,
            source = BleDeviceSource.PAIRED_LOCAL,
            category = BleDeviceCategory.HEART_RATE_SENSOR,
            isLastConnected = true
        )
        val sorted = BleDeviceClassifier.sortDevices(listOf(tv, localBand, lastBand))
        assertEquals(lastBand.address, sorted[0].address)
        assertEquals(localBand.address, sorted[1].address)
        assertEquals(tv.address, sorted[2].address)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GpsDashboardViewModel(app)
        vm.setAutoReconnectBle(false)
        assertFalse(vm.autoReconnectBle.value)
        vm.setAutoReconnectBle(true)
        assertTrue(vm.autoReconnectBle.value)
    }

    @Test
    fun `sample track seeder builds realistic karting 4-lap circuit and hiking mountain loop`() {
        val now = 1_750_000_000_000L
        val karting = SampleTrackSeeder.buildSampleKartingTrack(now)
        assertEquals(ActivityMode.KARTING, karting.session.activityMode)
        assertEquals(4, karting.session.lapCount)
        assertEquals(4, karting.laps.size)
        assertTrue("Expected > 150 karting points", karting.points.size > 150)
        assertTrue("Expected karting distance > 2000m, got ${karting.session.distanceMeters}", karting.session.distanceMeters > 2_000.0)
        assertTrue("Expected realistic karting peakG > 0.8G, got ${karting.session.peakTotalG}", karting.session.peakTotalG > 0.8f)
        assertTrue("Expected realistic karting HR > 140", (karting.session.avgHeartRate ?: 0) > 140)
        assertEquals(karting.laps.minOf { it.lapDurationMs }, karting.session.bestLapMs)

        val hiking = SampleTrackSeeder.buildSampleHikingTrack(now)
        assertEquals(ActivityMode.HIKING, hiking.session.activityMode)
        assertEquals(0, hiking.session.lapCount)
        assertTrue(hiking.laps.isEmpty())
        assertTrue("Expected > 100 hiking points", hiking.points.size > 100)
        assertTrue("Expected hiking distance > 3500m, got ${hiking.session.distanceMeters}", hiking.session.distanceMeters > 3_500.0)
        assertTrue("Expected hiking elevation gain > 200m, got ${hiking.session.elevationGainMeters}", hiking.session.elevationGainMeters > 200.0)
        assertTrue("Expected hiking max altitude > 550m", (hiking.session.maxAltitudeMeters ?: 0.0) > 550.0)

        // Ensure GPX & structured JSON export succeed cleanly on both sample tracks
        val gpx = GpxExporter.buildGpxXml(hiking.session, hiking.points)
        assertTrue(gpx.contains("<trkpt"))
        val json = JSONObject(TelemetryJsonExporter.buildStructuredJson(karting.session, karting.points, karting.laps))
        assertEquals(4, json.getJSONArray("laps").length())
    }

    @Test
    fun `social share bitmap renderer supports transparent png and solid presets plus stacked chart scrub and ai prompt`() {
        val now = 1_750_000_000_000L
        val hiking = SampleTrackSeeder.buildSampleHikingTrack(now)
        val karting = SampleTrackSeeder.buildSampleKartingTrack(now)

        // 1. Transparent PNG preserves alpha=0 corner pixels
        val transparentBmp = SocialCardBitmapRenderer.renderBitmap(
            detail = hiking,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(
                useImportedPhoto = false,
                bgPreset = SocialBgPreset.TRANSPARENT,
                showRoute = true,
                showTitle = true
            ),
            targetWidth = 540
        )
        assertEquals(540, transparentBmp.width)
        assertEquals(0, Color.alpha(transparentBmp.getPixel(2, 2)))
        transparentBmp.recycle()

        // 2. Solid color preset renders opaque background pixels
        val solidBmp = SocialCardBitmapRenderer.renderBitmap(
            detail = karting,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(
                useImportedPhoto = false,
                bgPreset = SocialBgPreset.OBSIDIAN,
                showRoute = true
            ),
            targetWidth = 540
        )
        assertEquals(255, Color.alpha(solidBmp.getPixel(2, 2)))
        solidBmp.recycle()

        // 3. Stacked hiking chart X-axis time alignment helper
        val startIdx = findNearestPointIndexByTimeFraction(hiking.points, 0f)
        val midIdx = findNearestPointIndexByTimeFraction(hiking.points, 0.5f)
        val endIdx = findNearestPointIndexByTimeFraction(hiking.points, 1f)
        assertEquals(0, startIdx)
        assertEquals(hiking.points.lastIndex, endIdx)
        assertTrue("Mid index should sit near center of track", midIdx in (hiking.points.size / 4)..(hiking.points.size * 3 / 4))

        // 4. AI analysis prompt + JSON clipboard payload
        val kartPromptPayload = TelemetryJsonExporter.buildClipboardPayload(
            session = karting.session,
            points = karting.points,
            laps = karting.laps,
            includeAiPrompt = true
        )
        assertTrue(kartPromptPayload.contains("卡丁车赛道遥测数据分析教练"))
        assertTrue(kartPromptPayload.contains("```json"))
        assertTrue(kartPromptPayload.contains("\"laps\""))

        val hikePlainPayload = TelemetryJsonExporter.buildClipboardPayload(
            session = hiking.session,
            points = hiking.points,
            laps = hiking.laps,
            includeAiPrompt = false
        )
        assertFalse(hikePlainPayload.contains("```json"))
        assertEquals("HIKING", JSONObject(hikePlainPayload).getString("activity_type"))
    }

    @Test
    fun `offroad crossing mode supports sample mountain route social card stats gpx and moto ai prompt`() {
        val now = 1_750_000_000_000L
        val offroad = SampleTrackSeeder.buildSampleOffroadCrossingTrack(now)

        assertEquals(ActivityMode.OFFROAD_CROSSING, offroad.session.activityMode)
        assertEquals("越野穿越", offroad.session.activityMode.labelZh)
        assertTrue(offroad.session.activityMode.isElevationTrail)
        assertTrue(offroad.laps.isEmpty())
        assertTrue("Expected > 100 offroad points", offroad.points.size > 100)
        assertTrue("Expected distance > 10km, got ${offroad.session.distanceMeters}", offroad.session.distanceMeters > 10_000.0)
        assertTrue("Expected mountain gain > 500m, got ${offroad.session.elevationGainMeters}", offroad.session.elevationGainMeters > 500.0)

        // Social card stats for offroad should show 最高时速 & 累计爬升 & 峰值 G 力 (not hiking pace)
        val badges = SocialCardBitmapRenderer.buildSelectedStats(
            detail = offroad,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(
                showElevation = true,
                showSpeed = true,
                showPeakG = true
            )
        )
        assertTrue(badges.any { it.label == "累计爬升" })
        assertTrue(badges.any { it.label == "最高时速" })
        assertFalse(badges.any { it.label == "平均配速" })

        val offroadBmp = SocialCardBitmapRenderer.renderBitmap(
            detail = offroad,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(bgPreset = SocialBgPreset.SUNSET_AMBER),
            targetWidth = 540
        )
        assertEquals(255, Color.alpha(offroadBmp.getPixel(2, 2)))
        offroadBmp.recycle()

        // GPX & AI coaching prompt for motorcycle off-road crossing
        val gpx = GpxExporter.buildGpxXml(offroad.session, offroad.points)
        assertTrue(gpx.contains("<type>offroad_crossing</type>"))

        val aiPayload = TelemetryJsonExporter.buildClipboardPayload(
            session = offroad.session,
            points = offroad.points,
            laps = offroad.laps,
            includeAiPrompt = true
        )
        assertTrue(aiPayload.contains("摩托车越野穿越"))
        assertTrue(aiPayload.contains("\"activity_type\": \"OFFROAD_CROSSING\""))
        assertTrue(aiPayload.contains("\"activity_label\": \"越野穿越\""))
    }

    @Test
    fun `offroad analysis engine grades climb difficulty and surface complexity and feeds social overlays and json`() {
        val now = 1_750_000_000_000L
        val offroad = SampleTrackSeeder.buildSampleOffroadCrossingTrack(now)
        val hiking = SampleTrackSeeder.buildSampleHikingTrack(now)

        val analysis = OffroadAnalysisEngine.analyze(offroad.session, offroad.points)
        assertTrue("Expected mountain climb score >= 45, got ${analysis.climb.score}", analysis.climb.score >= 45)
        assertTrue("Expected positive max grade, got ${analysis.climb.maxGradePct}", analysis.climb.maxGradePct > 4.0)
        assertTrue("Expected positive gain/km, got ${analysis.climb.gainPerKmMeters}", analysis.climb.gainPerKmMeters > 25.0)
        assertEquals(
            100,
            analysis.surface.smoothPct + analysis.surface.twistyPct + analysis.surface.technicalPct
        )
        assertTrue(analysis.climb.summaryZh.isNotBlank())
        assertTrue(analysis.surface.summaryZh.isNotBlank())

        // Compare against a flat straight road: mountain off-road must score strictly higher on both axes
        val flatPoints = (0..40).map { i ->
            TrackPointEntity(
                sessionId = 0L,
                timestampMs = now + i * 2_000L,
                latitude = 39.9000 + i * 0.0004,
                longitude = 116.4000,
                altitudeMeters = 50.0,
                speedMps = 13.5f,
                accuracyMeters = 3.0f,
                bearingDegrees = 0f,
                heartRateBpm = 95,
                lapNumber = 0,
                lateralG = 0.02f,
                longitudinalG = 0.01f,
                totalG = 0.03f
            )
        }
        val flatSession = TrackSessionEntity(
            name = "平路巡航测试",
            mode = ActivityMode.OFFROAD_CROSSING.name,
            startTimeMs = now,
            endTimeMs = now + 80_000L,
            durationMs = 80_000L,
            distanceMeters = 1800.0,
            maxSpeedMps = 14.0f,
            avgSpeedMps = 13.5f,
            elevationGainMeters = 2.0,
            elevationLossMeters = 2.0,
            minAltitudeMeters = 50.0,
            maxAltitudeMeters = 50.0,
            lapCount = 0,
            bestLapMs = null,
            avgHeartRate = 95,
            maxHeartRate = 98,
            pointCount = flatPoints.size
        )
        val flatAnalysis = OffroadAnalysisEngine.analyze(flatSession, flatPoints)
        assertTrue(analysis.climb.score > flatAnalysis.climb.score)
        assertTrue(analysis.surface.score > flatAnalysis.surface.score)

        // Social card overlay tags respect mode and individual toggles
        val bothTags = SocialCardBitmapRenderer.buildOffroadOverlayTags(
            detail = offroad,
            config = SocialCardConfig(
                showClimbDifficulty = true,
                showSurfaceComplexity = true
            )
        )
        assertEquals(2, bothTags.size)
        assertEquals("爬升难度", bothTags[0].category)
        assertEquals("路面复杂度", bothTags[1].category)
        assertTrue(bothTags[0].headline.contains(analysis.climb.level.code))
        assertTrue(bothTags[1].headline.contains(analysis.surface.level.code))

        val climbOnlyTags = SocialCardBitmapRenderer.buildOffroadOverlayTags(
            detail = offroad,
            config = SocialCardConfig(
                showClimbDifficulty = true,
                showSurfaceComplexity = false
            )
        )
        assertEquals(1, climbOnlyTags.size)
        assertEquals("爬升难度", climbOnlyTags[0].category)

        // Non-offroad tracks do not emit off-road rating pills
        assertTrue(
            SocialCardBitmapRenderer.buildOffroadOverlayTags(
                detail = hiking,
                config = SocialCardConfig(showClimbDifficulty = true, showSurfaceComplexity = true)
            ).isEmpty()
        )

        // Transparent PNG with offroad rating pills preserves alpha=0 corner pixel
        val stickerBmp = SocialCardBitmapRenderer.renderBitmap(
            detail = offroad,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(
                bgPreset = SocialBgPreset.TRANSPARENT,
                showRoute = true,
                showClimbDifficulty = true,
                showSurfaceComplexity = true
            ),
            targetWidth = 540
        )
        assertEquals(0, Color.alpha(stickerBmp.getPixel(2, 2)))
        stickerBmp.recycle()

        // Structured JSON includes offroad_analysis block
        val json = JSONObject(TelemetryJsonExporter.buildStructuredJson(offroad.session, offroad.points, offroad.laps))
        val offroadAnalysisJson = json.getJSONObject("session").getJSONObject("offroad_analysis")
        assertEquals(analysis.climb.score, offroadAnalysisJson.getJSONObject("climb_difficulty").getInt("score"))
        assertEquals(analysis.surface.score, offroadAnalysisJson.getJSONObject("surface_complexity").getInt("score"))
    }

    @Test
    fun `hiking and karting telemetry analysis compute difficulty surface and body stats and integrate into social overlays and json`() {
        val now = 1_750_000_000_000L
        val hiking = SampleTrackSeeder.buildSampleHikingTrack(now)
        val karting = SampleTrackSeeder.buildSampleKartingTrack(now)

        // 1. Hiking analysis: route difficulty + surface complexity + body performance
        val hikeAnalysis = TrackTelemetryAnalyzer.analyzeHiking(hiking.session, hiking.points)
        assertTrue(hikeAnalysis.difficulty.score in 15..100)
        assertEquals(
            100,
            hikeAnalysis.surface.smoothPct + hikeAnalysis.surface.twistyPct + hikeAnalysis.surface.technicalPct
        )
        assertTrue(hikeAnalysis.body.hasHeartRateData)
        assertEquals(
            100,
            hikeAnalysis.body.easyZonePct + hikeAnalysis.body.aerobicZonePct + hikeAnalysis.body.highZonePct
        )
        assertTrue(hikeAnalysis.body.estimatedCaloriesKcal >= 100)

        // Hiking social overlay emits 3 pills (难度评价, 路面复杂度, 身体表现)
        val hikeTags = SocialCardBitmapRenderer.buildAnalysisOverlayTags(
            detail = hiking,
            config = SocialCardConfig(
                showClimbDifficulty = true,
                showSurfaceComplexity = true,
                showBodyPerformance = true
            )
        )
        assertEquals(3, hikeTags.size)
        assertEquals("难度评价", hikeTags[0].category)
        assertEquals("路面复杂度", hikeTags[1].category)
        assertEquals("身体表现", hikeTags[2].category)

        val hikeSticker = SocialCardBitmapRenderer.renderBitmap(
            detail = hiking,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(
                bgPreset = SocialBgPreset.TRANSPARENT,
                showRoute = true,
                showClimbDifficulty = true,
                showSurfaceComplexity = true,
                showBodyPerformance = true
            ),
            targetWidth = 540
        )
        assertEquals(0, Color.alpha(hikeSticker.getPixel(2, 2)))
        hikeSticker.recycle()

        // 2. Karting analysis: track difficulty + body performance (no surface complexity)
        val kartAnalysis = TrackTelemetryAnalyzer.analyzeKarting(karting.session, karting.points, karting.laps)
        assertTrue("Expected kart difficulty >= 30, got ${kartAnalysis.difficulty.score}", kartAnalysis.difficulty.score >= 30)
        assertTrue(kartAnalysis.difficulty.peakLateralG > 0.5f)
        assertTrue(kartAnalysis.body.hasHeartRateData)
        assertEquals("圈速稳定", kartAnalysis.body.stabilityLabelZh)
        assertEquals(
            100,
            kartAnalysis.body.easyZonePct + kartAnalysis.body.aerobicZonePct + kartAnalysis.body.highZonePct
        )

        // Karting social overlay emits 2 pills (赛道难度, 身体表现)
        val kartTags = SocialCardBitmapRenderer.buildAnalysisOverlayTags(
            detail = karting,
            config = SocialCardConfig(
                showClimbDifficulty = true,
                showSurfaceComplexity = true,
                showBodyPerformance = true
            )
        )
        assertEquals(2, kartTags.size)
        assertEquals("赛道难度", kartTags[0].category)
        assertEquals("身体表现", kartTags[1].category)

        val kartPoster = SocialCardBitmapRenderer.renderBitmap(
            detail = karting,
            speedUnit = SpeedUnit.KMH,
            config = SocialCardConfig(
                bgPreset = SocialBgPreset.OBSIDIAN,
                showClimbDifficulty = true,
                showBodyPerformance = true
            ),
            targetWidth = 540
        )
        assertEquals(255, Color.alpha(kartPoster.getPixel(2, 2)))
        kartPoster.recycle()

        // 3. Structured JSON includes hiking_analysis & karting_analysis
        val hikeJson = JSONObject(TelemetryJsonExporter.buildStructuredJson(hiking.session, hiking.points, hiking.laps))
        val hikeAnalysisObj = hikeJson.getJSONObject("session").getJSONObject("hiking_analysis")
        assertEquals(hikeAnalysis.difficulty.score, hikeAnalysisObj.getJSONObject("route_difficulty").getInt("score"))
        assertEquals(hikeAnalysis.surface.score, hikeAnalysisObj.getJSONObject("surface_complexity").getInt("score"))
        assertEquals(hikeAnalysis.body.score, hikeAnalysisObj.getJSONObject("body_performance").getInt("score"))

        val kartJson = JSONObject(TelemetryJsonExporter.buildStructuredJson(karting.session, karting.points, karting.laps))
        val kartAnalysisObj = kartJson.getJSONObject("session").getJSONObject("karting_analysis")
        assertEquals(kartAnalysis.difficulty.score, kartAnalysisObj.getJSONObject("track_difficulty").getInt("score"))
        assertEquals(kartAnalysis.body.score, kartAnalysisObj.getJSONObject("body_performance").getInt("score"))
    }
}
