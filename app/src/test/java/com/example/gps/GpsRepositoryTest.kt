package com.example.gps

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GpsRepositoryTest {
    private lateinit var locationManager: ShadowLocationManager

    @Before
    fun startTracking() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        locationManager = shadowOf(app.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
        locationManager.setLocationEnabled(true)
        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        locationManager.setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
        GpsRepository.startTracking(app, "activity")
        GpsRepository.startTracking(app, "overlay")
        GpsRepository.stopTracking("activity")
        advanceSeconds(1)
        GpsRepository.resetAltitudeSession()
    }

    @After
    fun stopTracking() {
        GpsRepository.stopTracking("overlay")
    }

    @Test
    fun poorOrMissingSpeedDoesNotMasqueradeAsAStoppedReading() {
        assertNull(usableGpsSpeed(null, null))
        assertNull(usableGpsSpeed(12f, 2f))
        assertNull(usableGpsSpeed(Float.NaN, 0.2f))
        assertEquals(0f, requireNotNull(usableGpsSpeed(0f, 0.2f)), 0f)
        assertEquals(12f, requireNotNull(usableGpsSpeed(12f, null)), 0f)
    }

    @Test
    fun `fix without speed or altitude does not reuse old readings`() {
        publish(speedMps = 5f, altitudeM = 120.0)
        assertReadings("18.0", "120 m")

        advanceSeconds(1)
        publish()
        assertTrue(GpsRepository.gpsData.value.hasFix)
        assertReadings("--", "--")

        advanceSeconds(1)
        publish(speedMps = 0f, altitudeM = 135.0)
        assertReadings("0.0", "135 m")
    }

    @Test
    fun `fix expires without a callback and recovers on the next fix`() {
        publish(speedMps = 5f, altitudeM = 120.0)
        advanceSeconds(7)
        assertReadings("18.0", "120 m")

        advanceSeconds(1)
        assertFalse(GpsRepository.gpsData.value.hasFix)
        assertEquals(GpsFixStatus.SEARCHING, GpsRepository.gpsData.value.fixStatus)
        assertReadings("--", "--")

        publish(speedMps = 8f, altitudeM = 135.0)
        assertReadings("28.8", "135 m")
    }

    @Test
    fun `delayed GPS fix cannot revive old readings or block a fresh fallback`() {
        publish(speedMps = 5f, altitudeM = 120.0)
        advanceSeconds(9)
        publish(speedMps = 5f, altitudeM = 120.0, ageMs = 8_000L)
        assertReadings("--", "--")

        publish(provider = LocationManager.FUSED_PROVIDER, speedMps = 8f, altitudeM = 135.0, verticalAccuracyM = 9f)
        assertEquals(GpsFixStatus.FUSED_FALLBACK, GpsRepository.gpsData.value.fixStatus)
        assertReadings("28.8", "135 m")
    }

    @Test
    fun `live altitude rejects an isolated jump but follows a sustained climb`() {
        publish(altitudeM = 120.0)
        assertReadings("--", "120 m")

        advanceSeconds(1)
        publish(altitudeM = 160.0)
        val spike = GpsRepository.gpsData.value
        assertEquals(160.0, requireNotNull(spike.altitudeMeters), 0.0)
        assertEquals("120 m", spike.formattedAltitude())

        advanceSeconds(1)
        publish(altitudeM = 120.0)
        assertReadings("--", "120 m")

        repeat(6) {
            advanceSeconds(1)
            publish(altitudeM = 130.0)
        }
        val displayAltitude = requireNotNull(GpsRepository.gpsData.value.displayAltitudeMeters)
        assertTrue(displayAltitude in 128.0..130.0)
    }

    @Test
    fun `poor vertical accuracy never becomes live altitude or a recorded trend`() {
        publish(speedMps = 6f, altitudeM = 300.0, verticalAccuracyM = 80f)
        assertReadings("21.6", "--")
        assertNull(GpsRepository.altitudeTelemetry.value.maxAltitudeM)

        advanceSeconds(1)
        publish(speedMps = 6f, altitudeM = 120.0, verticalAccuracyM = 6f)
        assertReadings("21.6", "120 m")
        repeat(4) {
            advanceSeconds(1)
            publish(speedMps = 6f, altitudeM = 180.0, verticalAccuracyM = 70f)
        }
        assertReadings("21.6", "--")
        assertEquals(120.0, requireNotNull(GpsRepository.altitudeTelemetry.value.maxAltitudeM), 0.1)
    }

    @Test
    fun `two abrupt high accuracy spikes cannot move the live reading or trend`() {
        publish(altitudeM = 120.0, verticalAccuracyM = 6f)

        advanceSeconds(1)
        publish(altitudeM = 160.0, verticalAccuracyM = 6f)
        advanceSeconds(1)
        publish(altitudeM = 161.0, verticalAccuracyM = 6f)

        assertReadings("--", "120 m")
        assertEquals(120.0, requireNotNull(GpsRepository.altitudeTelemetry.value.maxAltitudeM), 0.1)

        advanceSeconds(1)
        publish(altitudeM = 160.0, verticalAccuracyM = 6f)
        assertReadings("--", "--")

        advanceSeconds(1)
        publish(altitudeM = 122.0, verticalAccuracyM = 6f)
        assertTrue(requireNotNull(GpsRepository.gpsData.value.displayAltitudeMeters) in 119.0..123.0)
    }

    @Test
    fun `fused coordinates stay usable without advertising an unqualified altitude`() {
        publish(provider = LocationManager.FUSED_PROVIDER, speedMps = 8f, altitudeM = 250.0)
        assertEquals(GpsFixStatus.FUSED_FALLBACK, GpsRepository.gpsData.value.fixStatus)
        assertReadings("28.8", "--")

        advanceSeconds(1)
        publish(provider = LocationManager.FUSED_PROVIDER, speedMps = 8f, altitudeM = 135.0, verticalAccuracyM = 8f)
        assertReadings("28.8", "135 m")
    }

    @Test
    fun `altitude recovery after a gap does not invent a climb`() {
        publish(altitudeM = 120.0, verticalAccuracyM = 6f)
        repeat(8) {
            advanceSeconds(1)
            publish(altitudeM = 180.0, verticalAccuracyM = 70f)
        }
        assertReadings("--", "--")

        advanceSeconds(1)
        publish(altitudeM = 140.0, verticalAccuracyM = 6f)
        assertReadings("--", "140 m")
        assertEquals(0.0, GpsRepository.altitudeTelemetry.value.elevationGainM, 0.1)
    }

    private fun publish(
        provider: String = LocationManager.GPS_PROVIDER,
        speedMps: Float? = null,
        altitudeM: Double? = null,
        ageMs: Long = 0L,
        verticalAccuracyM: Float? = null
    ) {
        val location = Location(provider).apply {
            latitude = 31.23
            longitude = 121.47
            accuracy = 5f
            time = System.currentTimeMillis() - ageMs
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - TimeUnit.MILLISECONDS.toNanos(ageMs)
            if (speedMps != null) speed = speedMps
            if (altitudeM != null) altitude = altitudeM
            if (verticalAccuracyM != null) verticalAccuracyMeters = verticalAccuracyM
        }
        locationManager.simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun advanceSeconds(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    private fun assertReadings(speed: String, altitude: String) {
        val data = GpsRepository.gpsData.value
        assertEquals(speed, data.formattedSpeedValue(SpeedUnit.KMH))
        assertEquals(altitude, data.formattedAltitude())
    }
}
