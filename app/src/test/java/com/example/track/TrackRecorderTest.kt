package com.example.track

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecorderTest {
    private val mainLooper get() = shadowOf(Looper.getMainLooper())
    private lateinit var app: Application
    private lateinit var locations: ShadowLocationManager
    private lateinit var sensors: ShadowSensorManager
    private lateinit var barometer: Sensor

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        locations = shadowOf(app.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        locations.setLocationEnabled(true)
        locations.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        sensors = shadowOf(app.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
        barometer = ShadowSensor.newInstance(Sensor.TYPE_PRESSURE)
        sensors.addSensor(barometer)
    }

    @After
    fun stopHike() {
        runBlocking { TrackRecorder.stopAndSave(app) }
        mainLooper.idle()
    }

    @Test
    fun `slow uphill recording accumulates climb with six seconds between fixes`() {
        TrackRecorder.startRecording(app, ActivityMode.HIKING)
        var result = RecordingSnapshot()
        for (point in 0..3) {
            result = recordFix(point, if (point == 0) 1 else 6, 100.0 + point * 6.0)
        }

        assertTrue("Expected sustained climb, recorded ${result.elevationGainMeters} m", result.elevationGainMeters >= 10.0)
    }

    @Test
    fun `poor first location does not set the track barometer baseline`() {
        TrackRecorder.startRecording(app, ActivityMode.HIKING)
        recordFix(point = 0, waitSeconds = 1, altitudeM = 300.0, horizontalAccuracyM = 50f, verticalAccuracyM = 80f, pressureHpa = 1000f)
        val result = recordFix(point = 1, waitSeconds = 1, altitudeM = 150.0, pressureHpa = 1000f)

        assertEquals(150.0, requireNotNull(result.maxAltitudeMeters), 0.1)
    }

    @Test
    fun `recording preserves the route point without saving its unreliable GPS altitude`() {
        TrackRecorder.startRecording(app, ActivityMode.HIKING)
        recordFix(point = 0, waitSeconds = 1, altitudeM = 120.0)
        recordFix(point = 1, waitSeconds = 1, altitudeM = 250.0, verticalAccuracyM = 80f)
        recordFix(point = 2, waitSeconds = 1, altitudeM = 122.0)

        val saved = runBlocking {
            val id = requireNotNull(TrackRecorder.stopAndSave(app))
            requireNotNull(TrackRepository(app).loadSessionDetail(id))
        }
        assertEquals(listOf(120.0, null, 122.0), saved.points.map { it.altitudeMeters })
        assertEquals(122.0, requireNotNull(saved.session.maxAltitudeMeters), 0.1)
    }

    @Test
    fun `saving includes delayed steps before finish and excludes steps after finish`() = runTest {
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val stepDetector = ShadowSensor.newInstance(Sensor.TYPE_STEP_DETECTOR)
        sensors.addSensor(stepDetector)
        TrackRecorder.startRecording(app, ActivityMode.HIKING)
        recordFix(point = 0, waitSeconds = 1, altitudeM = 100.0)
        recordFix(point = 1, waitSeconds = 1, altitudeM = 101.0)
        val finalStepNs = SystemClock.elapsedRealtimeNanos()
        mainLooper.idleFor(Duration.ofMillis(100))

        val saving = async(start = CoroutineStart.UNDISPATCHED) { TrackRecorder.stopAndSave(app) }
        mainLooper.idleFor(Duration.ofMillis(100))
        publishSensor(stepDetector, 1f, finalStepNs)
        publishSensor(stepDetector, 1f, SystemClock.elapsedRealtimeNanos())

        val savedId = requireNotNull(saving.await())
        val recordedSteps = TrackRepository(app).loadSessionDetail(savedId)?.session?.stepCount
        assertEquals(1, recordedSteps)
    }

    private fun recordFix(
        point: Int,
        waitSeconds: Long,
        altitudeM: Double,
        horizontalAccuracyM: Float = 4f,
        verticalAccuracyM: Float = 5f,
        pressureHpa: Float? = null
    ): RecordingSnapshot {
        mainLooper.idleFor(Duration.ofSeconds(waitSeconds))
        if (pressureHpa != null) {
            publishSensor(barometer, pressureHpa, SystemClock.elapsedRealtimeNanos())
        }
        locations.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 31.23 + point * 0.000014
            longitude = 121.47
            accuracy = horizontalAccuracyM
            verticalAccuracyMeters = verticalAccuracyM
            speed = 0.25f
            altitude = altitudeM
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
        mainLooper.idle()
        return runBlocking { withTimeout(5_000L) { TrackRecorder.snapshot.first { it.pointCount == point + 1 } } }
    }

    private fun publishSensor(device: Sensor, reading: Float, timestampNs: Long) {
        val event = ShadowSensorManager.createSensorEvent(1, device.type).apply {
            sensor = device
            timestamp = timestampNs
            values[0] = reading
        }
        sensors.sendSensorEventToListeners(event, device)
    }
}
