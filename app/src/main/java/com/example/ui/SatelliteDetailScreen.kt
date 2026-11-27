package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SatelliteAlt
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.GnssConstellation
import com.example.gps.GnssSatelliteInfo
import com.example.gps.GpsData
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteDetailScreen(
    gpsData: GpsData,
    satellites: List<GnssSatelliteInfo>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val cs = MaterialTheme.colorScheme
    var selectedConstellationName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedConstellation = selectedConstellationName?.let {
        runCatching { GnssConstellation.valueOf(it) }.getOrNull()
    }

    val countsByConstellation = remember(satellites) {
        satellites.groupingBy { it.constellation }.eachCount()
    }
    val filteredSatellites = remember(satellites, selectedConstellation) {
        if (selectedConstellation == null) satellites
        else satellites.filter { it.constellation == selectedConstellation }
    }

    val usedSats = remember(satellites) { satellites.filter { it.usedInFix } }
    val avgSnr = remember(usedSats, satellites) {
        val pool = if (usedSats.isNotEmpty()) usedSats else satellites.filter { it.cn0DbHz > 0f }
        if (pool.isEmpty()) null else pool.map { it.cn0DbHz }.average().toFloat()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("satellite_detail_screen"),
        containerColor = cs.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sat_detail_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_back_from_satellites")
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
        val summaryCard: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GpsStatusBadge(fixStatus = gpsData.fixStatus)
                        Text(
                            text = "${stringResource(R.string.telemetry_accuracy)} ${gpsData.formattedAccuracy()}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = cs.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SatSummaryStat(
                            label = stringResource(R.string.sat_used_label),
                            value = "${gpsData.satellitesUsed}",
                            accent = cs.secondary
                        )
                        SatSummaryStat(
                            label = stringResource(R.string.sat_visible_label),
                            value = "${gpsData.satellitesVisible}",
                            accent = cs.primary
                        )
                        SatSummaryStat(
                            label = stringResource(R.string.sat_avg_snr_label),
                            value = avgSnr?.let { String.format(Locale.US, "%.1f dB", it) } ?: "--",
                            accent = cs.onSurface
                        )
                    }
                }
            }
        }

        val skyplotCard: @Composable (Dp) -> Unit = { skyplotSize ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.sat_skyplot_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                        Text(
                            text = "${stringResource(R.string.sat_skyplot_legend_used)} · ${stringResource(R.string.sat_skyplot_legend_visible)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }

                    GnssSkyplotCanvas(
                        satellites = filteredSatellites,
                        modifier = Modifier.size(skyplotSize)
                    )
                }
            }
        }

        val constellationFilterRow: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConstellationFilterPill(
                    label = "${stringResource(R.string.filter_all)} (${satellites.size})",
                    dotColor = cs.primary,
                    selected = selectedConstellation == null,
                    onClick = { selectedConstellationName = null }
                )
                GnssConstellation.entries.forEach { constellation ->
                    val count = countsByConstellation[constellation] ?: 0
                    if (count > 0) {
                        ConstellationFilterPill(
                            label = "${constellation.labelZh} ($count)",
                            dotColor = constellationAccentColor(constellation, cs),
                            selected = selectedConstellation == constellation,
                            onClick = { selectedConstellationName = constellation.name }
                        )
                    }
                }
            }
        }

        val emptySatellitesCard: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SatelliteAlt,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = stringResource(R.string.sat_empty_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sat_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .displayCutoutPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isLandscape = maxWidth > maxHeight && maxWidth >= 540.dp
            if (isLandscape) {
                val skyplotSize = (maxHeight - 178.dp).coerceIn(168.dp, 260.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("satellite_landscape_layout"),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Pane: Summary & Enlarged Skyplot pinned side-by-side with channel list
                    Column(
                        modifier = Modifier
                            .weight(0.92f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        summaryCard()
                        skyplotCard(skyplotSize)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Right Pane: Independently scrollable Constellation Filters & Satellite Channel List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1.08f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (satellites.isNotEmpty()) {
                            item { constellationFilterRow() }
                        }

                        item {
                            Text(
                                text = stringResource(R.string.sat_list_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.primary,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        if (filteredSatellites.isEmpty()) {
                            item { emptySatellitesCard() }
                        } else {
                            items(
                                items = filteredSatellites,
                                key = { "${it.constellation.name}_${it.svid}" }
                            ) { sat ->
                                SatelliteChannelRow(sat = sat)
                            }
                        }

                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Box(modifier = Modifier.padding(top = 6.dp)) {
                            summaryCard()
                        }
                    }

                    item { skyplotCard(216.dp) }

                    if (satellites.isNotEmpty()) {
                        item { constellationFilterRow() }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.sat_list_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (filteredSatellites.isEmpty()) {
                        item { emptySatellitesCard() }
                    } else {
                        items(
                            items = filteredSatellites,
                            key = { "${it.constellation.name}_${it.svid}" }
                        ) { sat ->
                            SatelliteChannelRow(sat = sat)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GnssSkyplotCanvas(
    satellites: List<GnssSatelliteInfo>,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val bg = cs.surfaceContainer
    val ringColor = cs.outlineVariant
    val textColor = cs.onSurfaceVariant

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (size.minDimension / 2f) - 14.dp.toPx()
        if (radius <= 0f) return@Canvas

        drawCircle(color = bg, radius = radius, center = Offset(cx, cy))
        // Horizon (0°), 30°, and 60° elevation rings
        drawCircle(
            color = ringColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2.dp.toPx())
        )
        listOf(0.667f, 0.333f).forEach { frac ->
            drawCircle(
                color = ringColor.copy(alpha = 0.65f),
                radius = radius * frac,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 0.9.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            )
        }
        drawLine(
            color = ringColor.copy(alpha = 0.6f),
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 0.9.dp.toPx()
        )
        drawLine(
            color = ringColor.copy(alpha = 0.6f),
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 0.9.dp.toPx()
        )

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 11.sp.toPx()
            color = textColor.toArgb()
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText("北", cx, cy - radius - 3.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText("南", cx, cy + radius + 11.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText("东", cx + radius + 8.dp.toPx(), cy + 4.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText("西", cx - radius - 8.dp.toPx(), cy + 4.dp.toPx(), labelPaint)

        // Plot each satellite by (azimuth, elevation)
        satellites.forEach { sat ->
            val elevNorm = ((90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f)
            val azRad = (sat.azimuthDegrees - 90f) * PI / 180.0
            val r = elevNorm * (radius - 6.dp.toPx())
            val sx = cx + (cos(azRad) * r).toFloat()
            val sy = cy + (sin(azRad) * r).toFloat()
            val dotColor = constellationAccentColor(sat.constellation, cs)

            if (sat.usedInFix) {
                drawCircle(color = dotColor.copy(alpha = 0.28f), radius = 7.5.dp.toPx(), center = Offset(sx, sy))
                drawCircle(color = dotColor, radius = 4.2.dp.toPx(), center = Offset(sx, sy))
            } else {
                drawCircle(
                    color = dotColor.copy(alpha = 0.85f),
                    radius = 3.8.dp.toPx(),
                    center = Offset(sx, sy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun SatelliteChannelRow(sat: GnssSatelliteInfo) {
    val cs = MaterialTheme.colorScheme
    val accent = constellationAccentColor(sat.constellation, cs)
    val snrFraction = (sat.cn0DbHz / 48f).coerceIn(0f, 1f)
    val barColor = when {
        sat.cn0DbHz >= 32f -> cs.secondary
        sat.cn0DbHz >= 20f -> cs.primary
        else -> cs.tertiary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (sat.usedInFix) 0.20f else 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sat.constellation.shortCode,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = accent
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${sat.constellation.labelZh} #${sat.svid}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = cs.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (sat.usedInFix) cs.secondaryContainer else cs.surfaceContainerHigh
                        ) {
                            Text(
                                text = stringResource(if (sat.usedInFix) R.string.sat_badge_used else R.string.sat_badge_tracking),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (sat.usedInFix) cs.onSecondaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = String.format(Locale.US, "%.1f dB-Hz", sat.cn0DbHz),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = barColor
                    )
                }

                // Material 3 Signal Strength Bar
                val trackColor = cs.surfaceContainerHighest
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                ) {
                    val r = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(color = trackColor, size = size, cornerRadius = r)
                    if (snrFraction > 0.01f) {
                        drawRoundRect(
                            color = barColor,
                            size = Size(size.width * snrFraction, size.height),
                            cornerRadius = r
                        )
                    }
                }

                Text(
                    text = "仰角 ${sat.elevationDegrees.roundToInt()}° · 方位 ${sat.azimuthDegrees.roundToInt()}°",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SatSummaryStat(
    label: String,
    value: String,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            ),
            color = accent
        )
    }
}

@Composable
private fun ConstellationFilterPill(
    label: String,
    dotColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
            )
        }
    }
}

private fun constellationAccentColor(
    constellation: GnssConstellation,
    cs: androidx.compose.material3.ColorScheme
): Color {
    return when (constellation) {
        GnssConstellation.BEIDOU -> cs.error
        GnssConstellation.GPS -> cs.primary
        GnssConstellation.GALILEO -> cs.secondary
        GnssConstellation.GLONASS -> cs.tertiary
        else -> cs.onSurfaceVariant
    }
}
