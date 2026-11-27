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
    }

    @After
    fun stopTracking() {
        GpsRepository.stopTracking("overlay")
    }

    @Test
    fun `fix without speed or altitude does not reuse old readings`() {
        publish(speedMps = 5f, altitudeM = 120.0)
        assertReadings("18.0", "120.0 m")

        advanceSeconds(1)
        publish()
        assertTrue(GpsRepository.gpsData.value.hasFix)
        assertReadings("--", "--")

        advanceSeconds(1)
        publish(speedMps = 0f, altitudeM = 135.0)
        assertReadings("0.0", "135.0 m")
    }

    @Test
    fun `fix expires without a callback and recovers on the next fix`() {
        publish(speedMps = 5f, altitudeM = 120.0)
        advanceSeconds(7)
        assertReadings("18.0", "120.0 m")

        advanceSeconds(1)
        assertFalse(GpsRepository.gpsData.value.hasFix)
        assertEquals(GpsFixStatus.SEARCHING, GpsRepository.gpsData.value.fixStatus)
        assertReadings("--", "--")

        publish(speedMps = 8f, altitudeM = 135.0)
        assertReadings("28.8", "135.0 m")
    }

    @Test
    fun `delayed GPS fix cannot revive old readings or block a fresh fallback`() {
        publish(speedMps = 5f, altitudeM = 120.0)
        advanceSeconds(9)
        publish(speedMps = 5f, altitudeM = 120.0, ageMs = 8_000L)
        assertReadings("--", "--")

        publish(provider = LocationManager.FUSED_PROVIDER, speedMps = 8f, altitudeM = 135.0)
        assertEquals(GpsFixStatus.FUSED_FALLBACK, GpsRepository.gpsData.value.fixStatus)
        assertReadings("28.8", "135.0 m")
    }

    private fun publish(
        provider: String = LocationManager.GPS_PROVIDER,
        speedMps: Float? = null,
        altitudeM: Double? = null,
        ageMs: Long = 0L
    ) {
        val location = Location(provider).apply {
            latitude = 31.23
            longitude = 121.47
            accuracy = 5f
            time = System.currentTimeMillis() - ageMs
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - TimeUnit.MILLISECONDS.toNanos(ageMs)
            if (speedMps != null) speed = speedMps
            if (altitudeM != null) altitude = altitudeM
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
