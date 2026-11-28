package com.example.overlay

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.example.gps.GpsRepository
import com.example.gps.SpeedUnit
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FloatingGpsServiceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val location = shadowOf(app.getSystemService(LocationManager::class.java))
    private val mainLooper get() = shadowOf(Looper.getMainLooper())
    private var controller: ServiceController<FloatingGpsService>? = null

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        ShadowSettings.setCanDrawOverlays(true)
        location.setLocationEnabled(true)
        location.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    @After
    fun tearDown() {
        controller?.destroy()
        GpsRepository.stopTracking("visible_activity")
    }

    @Test
    fun withoutOverlayOrRecordingLeavingTheActivityImmediatelyStopsSampling() {
        GpsRepository.startTracking(app, "visible_activity")
        publishFix(speed = 5f, altitude = 120.0)
        assertTrue(GpsRepository.gpsData.value.hasFix)
        GpsRepository.stopTracking("visible_activity")
        assertFalse(GpsRepository.gpsData.value.hasFix)
        publishFix(speed = 8f, altitude = 135.0)
        assertFalse(GpsRepository.gpsData.value.hasFix)
    }

    @Test
    fun overlayKeepsReceivingSpeedAndAltitudeAfterTheActivityLeavesAndServiceRestarts() {
        GpsRepository.startTracking(app, "visible_activity")
        val first = createService()
        assertEquals(Service.START_STICKY, call(first, FloatingGpsService.ACTION_START))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, first.foregroundServiceType)
        assertTrue(GpsRepository.isOverlayRunning.value)

        GpsRepository.stopTracking("visible_activity")
        repeat(15) { publishFix(speed = 5f, altitude = 120.0) }
        assertEquals("18.0", GpsRepository.gpsData.value.formattedSpeedValue(SpeedUnit.KMH))
        assertEquals("120 m", GpsRepository.gpsData.value.formattedAltitude())

        controller?.destroy()
        val restarted = createService()
        assertEquals(Service.START_STICKY, restarted.onStartCommand(null, 0, 1))
        assertTrue(GpsRepository.isOverlayRunning.value)
        publishFix(speed = 8f, altitude = 135.0)
        assertEquals("28.8", GpsRepository.gpsData.value.formattedSpeedValue(SpeedUnit.KMH))
        assertEquals("135 m", GpsRepository.gpsData.value.formattedAltitude())
    }

    @Test
    fun stickyRestartOfRecordingOnlyServiceDoesNotTurnOnTheOverlay() {
        val first = createService()
        call(first, FloatingGpsService.ACTION_START_RECORDING_KEEPALIVE)
        assertFalse(GpsRepository.isOverlayRunning.value)
        controller?.destroy()

        val restarted = createService()
        assertEquals(Service.START_NOT_STICKY, restarted.onStartCommand(null, 0, 1))
        assertTrue(shadowOf(restarted).isStoppedBySelf)
        assertFalse(GpsRepository.isOverlayRunning.value)
    }

    @Test
    fun explicitHideStopsLocationButDoesNotRestartTheOverlay() {
        val first = createService()
        assertEquals(Service.START_STICKY, call(first, FloatingGpsService.ACTION_START))
        assertEquals(Service.START_NOT_STICKY, call(first, FloatingGpsService.ACTION_HIDE_OVERLAY))
        controller?.destroy()
        val restarted = createService()
        assertEquals(Service.START_NOT_STICKY, restarted.onStartCommand(null, 0, 1))
        publishFix(speed = 8f, altitude = 135.0)
        assertFalse(GpsRepository.gpsData.value.hasFix)
        assertFalse(GpsRepository.isOverlayRunning.value)
    }

    @Test
    fun closingOverlayWhileRecordingKeepsForegroundLocationSubscribed() {
        val service = createService()
        assertEquals(Service.START_STICKY, call(service, FloatingGpsService.ACTION_START))
        assertEquals(Service.START_STICKY, call(service, FloatingGpsService.ACTION_START_RECORDING_KEEPALIVE))
        assertEquals(Service.START_STICKY, call(service, FloatingGpsService.ACTION_HIDE_OVERLAY))
        assertFalse(GpsRepository.isOverlayRunning.value)
        assertFalse(shadowOf(service).isStoppedBySelf)
        publishFix(speed = 8f, altitude = 135.0)
        assertTrue(GpsRepository.gpsData.value.hasFix)
        assertEquals(Service.START_NOT_STICKY, call(service, FloatingGpsService.ACTION_STOP_RECORDING_KEEPALIVE))
    }

    @Test
    fun notificationReportsAnExpiredFixWithoutStoppingTheService() {
        val service = createService()
        assertEquals(Service.START_STICKY, call(service, FloatingGpsService.ACTION_START))
        publishFix(speed = 8f, altitude = 135.0)
        assertTrue(notificationText().contains("GPS 已定位"))
        mainLooper.idleFor(Duration.ofSeconds(8))
        assertTrue(notificationText().contains("定位中断"))
        assertTrue(GpsRepository.isOverlayRunning.value)
        publishFix(speed = 12f, altitude = 140.0)
        assertTrue(notificationText().contains("GPS 已定位"))
    }

    @Test
    fun foregroundWatchdogRenewsDroppedLocationSubscriptions() {
        val service = createService()
        assertEquals(Service.START_STICKY, call(service, FloatingGpsService.ACTION_START))
        publishFix(speed = 5f, altitude = 120.0)
        val manager = app.getSystemService(LocationManager::class.java)
        location.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).forEach(manager::removeUpdates)
        assertTrue(location.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty())
        mainLooper.idleFor(Duration.ofSeconds(26))
        assertTrue(GpsRepository.isOverlayRunning.value)
        assertTrue(location.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isNotEmpty())
        publishFix(speed = 8f, altitude = 135.0)
        assertEquals("28.8", GpsRepository.gpsData.value.formattedSpeedValue(SpeedUnit.KMH))
    }

    @Test
    fun foregroundStartRejectionReportsFailureAndNeverRegistersForLocation() {
        val service = createService()
        shadowOf(service).setThrowInStartForeground(SecurityException("Service cannot enter the foreground"))
        assertEquals(Service.START_NOT_STICKY, call(service, FloatingGpsService.ACTION_START))
        assertFalse(GpsRepository.isOverlayRunning.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(app.getString(com.example.R.string.toast_overlay_start_failed), ShadowToast.getTextOfLatestToast())
        publishFix(speed = 8f, altitude = 135.0)
        assertFalse(GpsRepository.gpsData.value.hasFix)
    }

    private fun notificationText(): String = shadowOf(app.getSystemService(NotificationManager::class.java))
        .allNotifications.single().extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun createService() = Robolectric.buildService(FloatingGpsService::class.java).create().also {
        controller = it
    }.get()

    private fun call(service: FloatingGpsService, action: String): Int =
        service.onStartCommand(Intent(app, FloatingGpsService::class.java).setAction(action), 0, 1)

    private fun publishFix(speed: Float, altitude: Double) {
        mainLooper.idleFor(Duration.ofSeconds(1))
        location.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 31.23
            longitude = 121.47
            accuracy = 4f
            verticalAccuracyMeters = 6f
            this.altitude = altitude
            this.speed = speed
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
        mainLooper.idle()
    }
}
