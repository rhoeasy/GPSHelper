package com.example.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.ble.HeartRateState
import com.example.gps.GpsData
import com.example.gps.GpsFixStatus
import com.example.gps.OverlayDisplayConfig
import com.example.gps.SpeedUnit
import com.example.sensor.GForceData
import com.example.ui.theme.GpsHudTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalTelemetryColors
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Jetpack Compose floating HUD component dynamically rendering user-selected telemetry
 * modules (Speed, G-Force radar & values, Altitude, Heart Rate, Bearing, Accuracy, Coordinates)
 * with Material 3 Day/Night/Auto theme sync.
 */
@Composable
fun OverlayWindow(
    gForceData: GForceData,
    gpsData: GpsData,
    speedUnit: SpeedUnit,
    heartRateState: HeartRateState,
    config: OverlayDisplayConfig,
    themeMode: ThemeMode,
    isMiniMode: Boolean,
    onToggleMiniMode: () -> Unit,
    onClose: () -> Unit,
    showCloseButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    GpsHudTheme(themeMode = themeMode) {
        val cs = MaterialTheme.colorScheme
        val tc = LocalTelemetryColors.current

        val gAccent = when {
            gForceData.totalG >= 1.0f -> cs.error
            gForceData.totalG >= 0.5f -> cs.tertiary
            else -> cs.primary
        }
        val statusDotColor = when (gpsData.fixStatus) {
            GpsFixStatus.LOCKED -> cs.secondary
            GpsFixStatus.FUSED_FALLBACK -> cs.primary
            GpsFixStatus.SEARCHING -> cs.tertiary
            GpsFixStatus.DISABLED, GpsFixStatus.NO_PERMISSION -> cs.error
        }

        val speedValueStr = gpsData.formattedSpeedValue(speedUnit)
        val altText = if (gpsData.hasFix && gpsData.altitudeMeters != null) {
            String.format(Locale.US, "%.1f m", gpsData.altitudeMeters)
        } else {
            "-- m"
        }
        val liveGText = String.format(Locale.US, "%.2fG", gForceData.totalG)
        val peakGText = String.format(Locale.US, "%.2fG", gForceData.peakG)
        val hrText = heartRateState.bpm?.let { "$it bpm" } ?: "-- bpm"
        val bearingText = gpsData.formattedBearing()
        val accuracyText = gpsData.formattedAccuracy()
        val coordsText = gpsData.formattedCoordinates() ?: "--, --"

        val hasRightColumn = config.showSpeed ||
            config.showAltitude ||
            config.showHeartRate ||
            config.showBearing ||
            config.showAccuracy ||
            config.showCoordinates ||
            config.showGForce

        Surface(
            modifier = modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onToggleMiniMode)
                .testTag("overlay_window_root"),
            shape = RoundedCornerShape(22.dp),
            color = cs.surfaceContainerLow.copy(alpha = 0.91f),
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
            shadowElevation = 6.dp
        ) {
            Box {
                Row(
                    modifier = Modifier.padding(
                        start = 10.dp,
                        top = 8.dp,
                        end = if (showCloseButton) 28.dp else 12.dp,
                        bottom = 8.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isMiniMode) 8.dp else 10.dp)
                ) {
                    if (config.showGForce) {
                        OverlayGForceDial(
                            gForceData = gForceData,
                            dotColor = gAccent,
                            modifier = Modifier
                                .size(if (isMiniMode) 36.dp else 56.dp)
                                .testTag("overlay_gforce_dial")
                        )
                    }

                    if (hasRightColumn) {
                        if (isMiniMode) {
                            // Ultra-compact single-line mini pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                var emittedCount = 0
                                @Composable
                                fun sep() {
                                    if (emittedCount > 0) {
                                        Text(
                                            text = "·",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = cs.outline
                                        )
                                    }
                                    emittedCount++
                                }

                                if (config.showSpeed) {
                                    sep()
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = speedValueStr,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            ),
                                            color = cs.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = speedUnit.label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (config.showAltitude) {
                                    sep()
                                    Text(
                                        text = altText,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                }
                                if (config.showGForce) {
                                    sep()
                                    Text(
                                        text = liveGText,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = gAccent,
                                        maxLines = 1
                                    )
                                }
                                if (config.showHeartRate) {
                                    sep()
                                    Text(
                                        text = "♥${heartRateState.bpm ?: "--"}",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        ),
                                        color = tc.heart,
                                        maxLines = 1
                                    )
                                }
                                if (config.showBearing && emittedCount < 3) {
                                    sep()
                                    Text(
                                        text = bearingText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = cs.primary,
                                        maxLines = 1
                                    )
                                }
                                if (config.showAccuracy && emittedCount == 0) {
                                    sep()
                                    Text(
                                        text = accuracyText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                }
                                if (config.showCoordinates && emittedCount == 0) {
                                    sep()
                                    Text(
                                        text = coordsText,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            // Standard compact multi-row HUD
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                var statusDotShown = false

                                @Composable
                                fun StatusDotOnce() {
                                    if (!statusDotShown) {
                                        statusDotShown = true
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(statusDotColor)
                                        )
                                    }
                                }

                                // Row 1: Speed (hero readout when enabled) + optional Bearing
                                if (config.showSpeed) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StatusDotOnce()
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = speedValueStr,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                    lineHeight = 22.sp
                                                ),
                                                color = cs.onSurface,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = speedUnit.label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = cs.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 2.dp),
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showBearing) {
                                            Text(
                                                text = bearingText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp
                                                ),
                                                color = cs.primary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Row 2: Altitude + Accuracy + Bearing (if Speed is off)
                                val showEnvRow = config.showAltitude ||
                                    config.showAccuracy ||
                                    (config.showBearing && !config.showSpeed)
                                if (showEnvRow) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StatusDotOnce()
                                        if (config.showAltitude) {
                                            Text(
                                                text = stringResource(R.string.telemetry_altitude),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = altText,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = if (config.showSpeed) 12.5.sp else 14.5.sp
                                                ),
                                                color = cs.onSurface,
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showBearing && !config.showSpeed) {
                                            Text(
                                                text = bearingText,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.sp
                                                ),
                                                color = cs.primary,
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showAccuracy) {
                                            Text(
                                                text = accuracyText,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 11.sp
                                                ),
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Row 3: G-Force + Heart Rate
                                if (config.showGForce || config.showHeartRate) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StatusDotOnce()
                                        if (config.showGForce) {
                                            Text(
                                                text = liveGText,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                ),
                                                color = gAccent,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${stringResource(R.string.gforce_label_peak)} $peakGText",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontSize = 11.sp
                                                ),
                                                color = cs.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        if (config.showHeartRate) {
                                            Text(
                                                text = "♥ $hrText",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                ),
                                                color = tc.heart,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // Row 4: Coordinates
                                if (config.showCoordinates) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        StatusDotOnce()
                                        Text(
                                            text = coordsText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = 10.5.sp
                                            ),
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showCloseButton) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 3.dp, end = 3.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClose)
                            .testTag("overlay_close_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.overlay_close_cd),
                            tint = cs.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayGForceDial(
    gForceData: GForceData,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dialBg = cs.surfaceContainerHighest.copy(alpha = 0.55f)
    val outerRing = cs.outline.copy(alpha = 0.55f)
    val innerRing = cs.outline.copy(alpha = 0.30f)
    val crosshair = cs.outlineVariant.copy(alpha = 0.55f)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) - 2.dp.toPx()
        if (radius <= 0f) return@Canvas

        drawCircle(color = dialBg, radius = radius, center = Offset(cx, cy))
        drawCircle(
            color = outerRing,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.3.dp.toPx())
        )
        drawCircle(
            color = innerRing,
            radius = radius * 0.5f,
            center = Offset(cx, cy),
            style = Stroke(
                width = 0.9.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            )
        )
        drawLine(
            color = crosshair,
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = crosshair,
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 0.8.dp.toPx()
        )

        val maxScaleG = 1.5f
        val maxTravel = radius - 3.2.dp.toPx()

        fun mapPoint(latG: Float, longG: Float): Offset {
            val nx = latG / maxScaleG
            val ny = -longG / maxScaleG
            val mag = hypot(nx, ny)
            val scale = if (mag > 1f) 1f / mag else 1f
            return Offset(cx + nx * scale * maxTravel, cy + ny * scale * maxTravel)
        }

        val trail = gForceData.trail
        if (trail.size > 1) {
            trail.forEachIndexed { index, sample ->
                val fraction = (index + 1f) / trail.size.toFloat()
                drawCircle(
                    color = dotColor.copy(alpha = fraction * 0.40f),
                    radius = (1.1f + 1.3f * fraction).dp.toPx(),
                    center = mapPoint(sample.lateralG, sample.longitudinalG)
                )
            }
        }

        val dotCenter = mapPoint(gForceData.lateralG, gForceData.longitudinalG)
        val coreRadius = if (size.minDimension < 46.dp.toPx()) 3.2.dp.toPx() else 4.2.dp.toPx()
        drawCircle(
            color = dotColor.copy(alpha = 0.28f),
            radius = coreRadius * 1.7f,
            center = dotCenter
        )
        drawCircle(
            color = dotColor,
            radius = coreRadius,
            center = dotCenter
        )
    }
}

/**
 * WindowManager host for the Jetpack Compose [OverlayWindow].
 *
 * Supplies LifecycleOwner, SavedStateRegistryOwner, and ViewModelStoreOwner so ComposeView
 * operates cleanly inside an Android Foreground Service using TYPE_APPLICATION_OVERLAY
 * (always displayed on top of other apps without stealing focus), with jitter-free screen-space
 * drag repositioning and live sync with [OverlayDisplayConfig].
 */
class OverlayWindowController(
    private val context: Context,
    private val gForceFlow: StateFlow<GForceData>,
    private val gpsFlow: StateFlow<GpsData>,
    private val speedUnitFlow: StateFlow<SpeedUnit>,
    private val heartRateFlow: StateFlow<HeartRateState>,
    private val overlayConfigFlow: StateFlow<OverlayDisplayConfig>,
    private val themeModeFlow: StateFlow<ThemeMode>,
    private val onCloseRequested: () -> Unit
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private var hostContainer: DraggableOverlayContainer? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isMiniMode by mutableStateOf(false)
    private var isAttached = false

    fun attach(): Boolean {
        if (isAttached) return true

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val params = createOverlayLayoutParams(context)

        val container = DraggableOverlayContainer(
            context = context,
            onDragDelta = { dx, dy ->
                params.x += dx
                params.y += dy
                val root = hostContainer
                if (root != null) {
                    clampPosition(params, root)
                    runCatching { windowManager.updateViewLayout(root, params) }
                }
            }
        ).apply {
            setViewTreeLifecycleOwner(this@OverlayWindowController)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindowController)
            setViewTreeViewModelStoreOwner(this@OverlayWindowController)
        }

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val gForce by gForceFlow.collectAsStateWithLifecycle()
                val gps by gpsFlow.collectAsStateWithLifecycle()
                val speedUnit by speedUnitFlow.collectAsStateWithLifecycle()
                val heartRate by heartRateFlow.collectAsStateWithLifecycle()
                val overlayConfig by overlayConfigFlow.collectAsStateWithLifecycle()
                val themeMode by themeModeFlow.collectAsStateWithLifecycle()

                // Re-clamp window bounds whenever displayed modules change pill dimensions
                LaunchedEffect(overlayConfig, isMiniMode) {
                    hostContainer?.post {
                        val root = hostContainer ?: return@post
                        clampPosition(params, root)
                        runCatching { windowManager.updateViewLayout(root, params) }
                    }
                }

                OverlayWindow(
                    gForceData = gForce,
                    gpsData = gps,
                    speedUnit = speedUnit,
                    heartRateState = heartRate,
                    config = overlayConfig,
                    themeMode = themeMode,
                    isMiniMode = isMiniMode,
                    onToggleMiniMode = { isMiniMode = !isMiniMode },
                    onClose = onCloseRequested
                )
            }
        }

        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return try {
            windowManager.addView(container, params)
            hostContainer = container
            layoutParams = params
            isAttached = true
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Compose OverlayWindow", e)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
            false
        }
    }

    fun onConfigurationChanged() {
        val root = hostContainer ?: return
        val params = layoutParams ?: return
        clampPosition(params, root)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    fun detach() {
        if (!isAttached) return
        isAttached = false

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        val root = hostContainer
        hostContainer = null
        layoutParams = null

        if (root != null && root.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(root) }
                .onFailure {
                    runCatching { windowManager.removeView(root) }
                }
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    private fun clampPosition(params: WindowManager.LayoutParams, view: View) {
        val metrics = context.resources.displayMetrics
        val viewWidth = view.width.takeIf { it > 0 } ?: 220
        val viewHeight = view.height.takeIf { it > 0 } ?: 80
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private class DraggableOverlayContainer(
        context: Context,
        private val onDragDelta: (dx: Int, dy: Int) -> Unit
    ) : FrameLayout(context) {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    dragging = false
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val totalDx = ev.rawX - downRawX
                    val totalDy = ev.rawY - downRawY
                    if (!dragging && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        dragging = true
                        lastRawX = ev.rawX
                        lastRawY = ev.rawY
                        return true
                    }
                    return dragging
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return false
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        val dx = (event.rawX - lastRawX).toInt()
                        val dy = (event.rawY - lastRawY).toInt()
                        if (dx != 0 || dy != 0) {
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            onDragDelta(dx, dy)
                        }
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    companion object {
        private const val TAG = "OverlayWindowController"

        fun createOverlayLayoutParams(context: Context): WindowManager.LayoutParams {
            val density = context.resources.displayMetrics.density
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (16f * density + 0.5f).toInt()
                y = (96f * density + 0.5f).toInt()
            }
        }
    }
}
