package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.alert.SafetyAlertMonitor
import com.example.ble.HeartRateRepository
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsRepository
import com.example.gps.SafetyAlertConfig
import com.example.sensor.GForceData
import com.example.sensor.MotionSensorManager
import com.example.track.RecordingSnapshot
import com.example.track.TrackFormatters
import com.example.track.TrackRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Unified Foreground Location Service powering:
 * 1. The draggable Jetpack Compose [OverlayWindow] (`TYPE_APPLICATION_OVERLAY`), and/or
 * 2. Background GPS + IMU keep-alive during active Karting / Hiking track recording
 *    (preventing OEM sleep kills when the screen is off in a pocket).
 *
 * The service stays alive while either mode is active and cleanly stops itself when both end.
 */
class FloatingGpsService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var notificationManager: NotificationManager? = null
    private var overlayController: OverlayWindowController? = null
    private var telemetryJob: Job? = null
    private var lastNotificationUpdateMs = 0L

    private var isForegroundStarted = false
    private var isRecordingKeepAlive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        GpsRepository.ensurePrefsLoaded(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_HIDE_OVERLAY -> {
                detachOverlay()
                if (!isRecordingKeepAlive) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                refreshNotificationNow()
                return START_STICKY
            }

            ACTION_STOP_RECORDING_KEEPALIVE -> {
                isRecordingKeepAlive = false
                if (overlayController == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                refreshNotificationNow()
                return START_STICKY
            }

            ACTION_START_RECORDING_KEEPALIVE -> {
                if (!GpsRepository.hasLocationPermission(this)) {
                    if (overlayController == null) stopSelf()
                    return START_NOT_STICKY
                }
                isRecordingKeepAlive = true
                if (!ensureForegroundAndSensors()) {
                    isRecordingKeepAlive = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                refreshNotificationNow()
                return START_STICKY
            }

            else -> {
                // Default / ACTION_START: Show floating overlay window
                if (!Settings.canDrawOverlays(this) || !GpsRepository.hasLocationPermission(this)) {
                    Log.w(TAG, "Cannot start floating overlay: missing overlay or location permission")
                    if (!isRecordingKeepAlive) stopSelf()
                    return START_NOT_STICKY
                }
                if (!ensureForegroundAndSensors()) {
                    if (!isRecordingKeepAlive) stopSelf()
                    return START_NOT_STICKY
                }
                if (overlayController == null) {
                    val controller = OverlayWindowController(
                        context = this,
                        gForceFlow = MotionSensorManager.gForceData,
                        gpsFlow = GpsRepository.gpsData,
                        speedUnitFlow = GpsRepository.speedUnit,
                        heartRateFlow = HeartRateRepository.heartRateState,
                        overlayConfigFlow = GpsRepository.overlayConfig,
                        safetyAlertConfigFlow = GpsRepository.safetyAlertConfig,
                        themeModeFlow = GpsRepository.themeMode,
                        onCloseRequested = {
                            detachOverlay()
                            if (!isRecordingKeepAlive) {
                                stopSelf()
                            } else {
                                refreshNotificationNow()
                            }
                        }
                    )
                    if (controller.attach()) {
                        overlayController = controller
                        GpsRepository.setOverlayRunning(true)
                    } else if (!isRecordingKeepAlive) {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                refreshNotificationNow()
                return START_STICKY
            }
        }
    }

    private fun ensureForegroundAndSensors(): Boolean {
        if (!isForegroundStarted) {
            val initialNotification = buildNotification(
                gpsData = GpsRepository.gpsData.value,
                gForce = MotionSensorManager.gForceData.value,
                recording = TrackRecorder.snapshot.value
            )
            try {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
                ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, fgsType)
                isForegroundStarted = true
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException starting foreground location service", se)
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter foreground state", e)
                return false
            }
        }

        GpsRepository.startTracking(applicationContext, CLIENT_ID)
        MotionSensorManager.startListening(applicationContext, CLIENT_ID)
        startTelemetryObserverIfNeeded()
        return true
    }

    private fun detachOverlay() {
        overlayController?.detach()
        overlayController = null
        GpsRepository.setOverlayRunning(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        MotionSensorManager.refreshDisplayRotation(this)
        overlayController?.onConfigurationChanged()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        detachOverlay()
        isRecordingKeepAlive = false
        isForegroundStarted = false
        GpsRepository.stopTracking(CLIENT_ID)
        MotionSensorManager.stopListening(CLIENT_ID)
        super.onDestroy()
    }

    private data class ServiceTelemetryBundle(
        val gps: GpsData,
        val gForce: GForceData,
        val hr: HeartRateState,
        val alertCfg: SafetyAlertConfig,
        val recording: RecordingSnapshot
    )

    private fun startTelemetryObserverIfNeeded() {
        if (telemetryJob != null) return
        telemetryJob = serviceScope.launch {
            combine(
                GpsRepository.gpsData,
                MotionSensorManager.gForceData,
                HeartRateRepository.heartRateState,
                GpsRepository.safetyAlertConfig,
                TrackRecorder.snapshot
            ) { gps, gForce, hr, alertCfg, recording ->
                ServiceTelemetryBundle(gps, gForce, hr, alertCfg, recording)
            }.collect { bundle ->
                val alertState = SafetyAlertMonitor.evaluate(bundle.gps, bundle.hr, bundle.alertCfg)
                SafetyAlertMonitor.maybeVibrate(applicationContext, alertState, bundle.alertCfg)
                maybeUpdateNotification(bundle.gps, bundle.gForce, bundle.recording)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun refreshNotificationNow() {
        if (!isForegroundStarted) return
        lastNotificationUpdateMs = SystemClock.elapsedRealtime()
        runCatching {
            notificationManager?.notify(
                NOTIFICATION_ID,
                buildNotification(
                    gpsData = GpsRepository.gpsData.value,
                    gForce = MotionSensorManager.gForceData.value,
                    recording = TrackRecorder.snapshot.value
                )
            )
        }
    }

    private fun maybeUpdateNotification(
        gps: GpsData,
        gForce: GForceData,
        recording: RecordingSnapshot
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdateMs < NOTIFICATION_THROTTLE_MS) return
        lastNotificationUpdateMs = now
        runCatching {
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(gps, gForce, recording))
        }
    }

    private fun buildNotification(
        gpsData: GpsData,
        gForce: GForceData,
        recording: RecordingSnapshot
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hasOverlay = overlayController != null
        val isRecording = isRecordingKeepAlive || recording.isActive

        val title = when {
            hasOverlay && isRecording -> getString(R.string.notification_title_both)
            isRecording -> getString(R.string.notification_title_recording)
            else -> getString(R.string.notification_title)
        }

        val altStr = if (gpsData.hasFix && gpsData.altitudeMeters != null) {
            String.format(Locale.US, "海拔 %.1f m", gpsData.altitudeMeters)
        } else {
            getString(R.string.notification_waiting_fix)
        }

        val contentText = if (isRecording) {
            val dist = TrackFormatters.formatDistance(recording.distanceMeters)
            val dur = TrackFormatters.formatDuration(recording.elapsedMs)
            "${recording.mode.labelZh} $dist · $dur · $altStr"
        } else {
            val gStr = String.format(Locale.US, "%.2f G (峰值 %.2f G)", gForce.totalG, gForce.peakG)
            "$altStr  ·  $gStr"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hud_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)

        if (hasOverlay) {
            val stopOverlayIntent = Intent(this, FloatingGpsService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            val stopOverlayPendingIntent = PendingIntent.getService(
                this,
                1,
                stopOverlayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                0,
                getString(R.string.action_stop_overlay),
                stopOverlayPendingIntent
            )
        }

        return builder.build()
    }

    companion object {
        private const val TAG = "FloatingGpsService"
        private const val CLIENT_ID = "floating_gps_service"
        private const val CHANNEL_ID = "gps_overlay_hud_channel"
        private const val NOTIFICATION_ID = 1042
        private const val NOTIFICATION_THROTTLE_MS = 1_000L

        const val ACTION_START = "com.example.overlay.ACTION_START"
        const val ACTION_STOP = "com.example.overlay.ACTION_STOP"
        const val ACTION_HIDE_OVERLAY = "com.example.overlay.ACTION_HIDE_OVERLAY"
        const val ACTION_START_RECORDING_KEEPALIVE = "com.example.overlay.ACTION_START_RECORDING_KEEPALIVE"
        const val ACTION_STOP_RECORDING_KEEPALIVE = "com.example.overlay.ACTION_STOP_RECORDING_KEEPALIVE"

        fun start(context: Context) {
            val intent = Intent(context, FloatingGpsService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingGpsService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            runCatching { context.startService(intent) }
                .onFailure {
                    runCatching { context.stopService(Intent(context, FloatingGpsService::class.java)) }
                }
        }

        fun startRecordingKeepAlive(context: Context) {
            if (!GpsRepository.hasLocationPermission(context)) return
            val intent = Intent(context, FloatingGpsService::class.java).apply {
                action = ACTION_START_RECORDING_KEEPALIVE
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stopRecordingKeepAlive(context: Context) {
            val intent = Intent(context, FloatingGpsService::class.java).apply {
                action = ACTION_STOP_RECORDING_KEEPALIVE
            }
            runCatching { context.startService(intent) }
        }
    }
}
