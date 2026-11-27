package com.example.ui

import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.GpsData
import com.example.sensor.CompassMath
import com.example.sensor.EnvironmentTelemetry
import com.example.ui.theme.CoordinatesMonoStyle
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassDetailScreen(
    gpsData: GpsData,
    envTelemetry: EnvironmentTelemetry,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val cs = MaterialTheme.colorScheme
    var useTrueNorth by rememberSaveable { mutableStateOf(true) }

    val declinationDeg = remember(gpsData.hasFix, gpsData.latitude, gpsData.longitude, gpsData.altitudeMeters) {
        if (gpsData.hasFix && gpsData.latitude != null && gpsData.longitude != null) {
            CompassMath.computeDeclinationDegrees(
                latitude = gpsData.latitude,
                longitude = gpsData.longitude,
                altitudeMeters = gpsData.altitudeMeters ?: 0.0
            )
        } else {
            null
        }
    }

    val activeHeading = if (envTelemetry.hasCompassReading) {
        envTelemetry.headingDegrees(useTrueNorth = useTrueNorth, declinationDeg = declinationDeg)
    } else {
        gpsData.bearingDegrees ?: 0f
    }
    val hasAnyHeading = envTelemetry.hasCompassReading || (gpsData.hasFix && gpsData.bearingDegrees != null)
    val cardinalZh = CompassMath.compassCardinalLabelZh(activeHeading)

    val needsCalibration = envTelemetry.hasCompass &&
        (envTelemetry.compassAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
            (envTelemetry.magneticFieldUt != null &&
                (envTelemetry.magneticFieldUt < 20f || envTelemetry.magneticFieldUt > 75f)))

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("compass_detail_screen"),
        containerColor = cs.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.compass_detail_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_from_compass")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back_dashboard_cd)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onSurface,
                    navigationIconContentColor = cs.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero Compass Dial Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = cs.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Top mode row: True North / Magnetic North segmented toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (hasAnyHeading) "${activeHeading.roundToInt()}° $cardinalZh" else "--°",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 34.sp
                                    ),
                                    color = cs.onSurface,
                                    modifier = Modifier.testTag("compass_heading_text")
                                )
                                val declLabel = if (declinationDeg != null) {
                                    "${stringResource(R.string.compass_declination_prefix)} ${String.format(Locale.US, "%+.1f°", declinationDeg)}"
                                } else {
                                    stringResource(R.string.compass_mode_magnetic)
                                }
                                Text(
                                    text = declLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(cs.surfaceContainer)
                                    .padding(3.dp)
                            ) {
                                listOf(
                                    true to R.string.compass_mode_true,
                                    false to R.string.compass_mode_magnetic
                                ).forEach { (isTrue, labelRes) ->
                                    val selected = useTrueNorth == isTrue
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (selected) cs.primaryContainer else Color.Transparent)
                                            .clickable { useTrueNorth = isTrue }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag(if (isTrue) "compass_true_north" else "compass_mag_north"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Material 3 High-Precision Rotating Compass Rose + Center Spirit Level
                        MaterialCompassDial(
                            headingDeg = activeHeading,
                            gpsBearingDeg = if (gpsData.hasFix) gpsData.bearingDegrees else null,
                            pitchDeg = envTelemetry.pitchDeg,
                            rollDeg = envTelemetry.rollDeg,
                            modifier = Modifier.size(252.dp)
                        )

                        if (!envTelemetry.hasCompass) {
                            Text(
                                text = stringResource(R.string.compass_no_sensor),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }

                // Calibration / Magnetic interference advisory banner (only when needed)
                if (needsCalibration) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = cs.tertiaryContainer.copy(alpha = 0.8f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = cs.onTertiaryContainer,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.compass_calibrate_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onTertiaryContainer
                            )
                        }
                    }
                }

                // 2-column Navigation & Magnetic Field Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(cs.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Navigation,
                                        contentDescription = null,
                                        tint = cs.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.compass_gps_course_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Text(
                                text = gpsData.formattedBearing(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 19.sp
                                ),
                                color = if (gpsData.hasFix && gpsData.bearingDegrees != null) cs.onSurface else cs.outline
                            )
                            Text(
                                text = stringResource(R.string.compass_gps_course_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = cs.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(cs.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Explore,
                                        contentDescription = null,
                                        tint = cs.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.compass_field_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Text(
                                text = envTelemetry.magneticFieldUt?.let {
                                    String.format(Locale.US, "%.1f µT", it)
                                } ?: "--",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 19.sp
                                ),
                                color = cs.onSurface
                            )
                            val accLabel = when (envTelemetry.compassAccuracy) {
                                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> stringResource(R.string.compass_accuracy_high)
                                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> stringResource(R.string.compass_accuracy_medium)
                                else -> stringResource(R.string.compass_accuracy_low)
                            }
                            Text(
                                text = accLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (needsCalibration) cs.tertiary else cs.secondary
                            )
                        }
                    }
                }

                // Spirit Level (Pitch & Roll) + Coordinates Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = cs.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Straighten,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.compass_level_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cs.surfaceContainer)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.compass_pitch_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.1f°", envTelemetry.pitchDeg),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = cs.onSurface
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.compass_roll_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%+.1f°", envTelemetry.rollDeg),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = cs.onSurface
                                )
                            }
                        }

                        gpsData.formattedCoordinates()?.let { coords ->
                            Text(
                                text = coords,
                                style = CoordinatesMonoStyle.copy(fontSize = 13.5.sp),
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MaterialCompassDial(
    headingDeg: Float,
    gpsBearingDeg: Float?,
    pitchDeg: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dialSurface = cs.surfaceContainer
    val outerRingColor = cs.outlineVariant
    val tickMinor = cs.outline.copy(alpha = 0.45f)
    val tickMajor = cs.onSurfaceVariant
    val northRed = cs.error
    val textColor = cs.onSurface
    val gpsAccent = cs.primary
    val levelBubbleColor = cs.secondary

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) - 12.dp.toPx()
        if (radius <= 0f) return@Canvas

        // Dial background circle
        drawCircle(color = dialSurface, radius = radius, center = Offset(cx, cy))
        drawCircle(
            color = outerRingColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Fixed top lubber line (rider's forward pointing reference)
        val lubberPath = Path().apply {
            moveTo(cx, cy - radius - 10.dp.toPx())
            lineTo(cx - 5.dp.toPx(), cy - radius - 2.dp.toPx())
            lineTo(cx + 5.dp.toPx(), cy - radius - 2.dp.toPx())
            close()
        }
        drawPath(path = lubberPath, color = northRed)

        // Rotating compass rose (-headingDeg so North stays aligned with real world)
        rotate(degrees = -headingDeg, pivot = Offset(cx, cy)) {
            for (deg in 0 until 360 step 5) {
                val rad = (deg - 90) * PI / 180.0
                val isCardinal = deg % 90 == 0
                val isMajor = deg % 30 == 0
                val innerR = when {
                    isCardinal -> radius - 14.dp.toPx()
                    isMajor -> radius - 10.dp.toPx()
                    else -> radius - 6.dp.toPx()
                }
                val outerR = radius - 2.dp.toPx()
                val start = Offset(cx + (cos(rad) * innerR).toFloat(), cy + (sin(rad) * innerR).toFloat())
                val end = Offset(cx + (cos(rad) * outerR).toFloat(), cy + (sin(rad) * outerR).toFloat())
                drawLine(
                    color = if (deg == 0) northRed else if (isMajor) tickMajor else tickMinor,
                    start = start,
                    end = end,
                    strokeWidth = if (isCardinal) 2.4.dp.toPx() else if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Red North triangle inside the ring
            val northTri = Path().apply {
                moveTo(cx, cy - radius + 16.dp.toPx())
                lineTo(cx - 5.dp.toPx(), cy - radius + 24.dp.toPx())
                lineTo(cx + 5.dp.toPx(), cy - radius + 24.dp.toPx())
                close()
            }
            drawPath(path = northTri, color = northRed)

            // Cardinal letters (北 / 东 / 南 / 西)
            val labelRadius = radius - 34.dp.toPx()
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 15.sp.toPx()
                isFakeBoldText = true
            }
            listOf(
                0 to ("北" to northRed),
                90 to ("东" to textColor),
                180 to ("南" to textColor),
                270 to ("西" to textColor)
            ).forEach { (deg, pair) ->
                val (label, col) = pair
                val rad = (deg - 90) * PI / 180.0
                val lx = cx + (cos(rad) * labelRadius).toFloat()
                val ly = cy + (sin(rad) * labelRadius).toFloat() + 5.dp.toPx()
                paint.color = col.toArgb()
                drawContext.canvas.nativeCanvas.drawText(label, lx, ly, paint)
            }

            // Optional GPS actual course-over-ground marker on the ring
            if (gpsBearingDeg != null) {
                val gpsRad = (gpsBearingDeg - 90f) * PI / 180.0
                val gx = cx + (cos(gpsRad) * (radius - 2.dp.toPx())).toFloat()
                val gy = cy + (sin(gpsRad) * (radius - 2.dp.toPx())).toFloat()
                drawCircle(color = gpsAccent, radius = 5.dp.toPx(), center = Offset(gx, gy))
            }
        }

        // Center 2-axis bubble spirit level
        val levelRadius = radius * 0.30f
        drawCircle(
            color = outerRingColor.copy(alpha = 0.65f),
            radius = levelRadius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.1.dp.toPx())
        )
        drawLine(
            color = outerRingColor.copy(alpha = 0.55f),
            start = Offset(cx - levelRadius * 0.65f, cy),
            end = Offset(cx + levelRadius * 0.65f, cy),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = outerRingColor.copy(alpha = 0.55f),
            start = Offset(cx, cy - levelRadius * 0.65f),
            end = Offset(cx, cy + levelRadius * 0.65f),
            strokeWidth = 1.dp.toPx()
        )

        val bubbleX = (cx + (rollDeg / 45f).coerceIn(-1f, 1f) * (levelRadius - 7.dp.toPx()))
        val bubbleY = (cy + (pitchDeg / 45f).coerceIn(-1f, 1f) * (levelRadius - 7.dp.toPx()))
        drawCircle(
            color = levelBubbleColor.copy(alpha = 0.32f),
            radius = 9.dp.toPx(),
            center = Offset(bubbleX, bubbleY)
        )
        drawCircle(
            color = levelBubbleColor,
            radius = 5.dp.toPx(),
            center = Offset(bubbleX, bubbleY)
        )
    }
}
