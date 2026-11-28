package com.example.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.gps.DashboardSpeedStyle
import com.example.gps.SpeedUnit
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun speedDialMaximum(style: DashboardSpeedStyle, unit: SpeedUnit): Int = when {
    style == DashboardSpeedStyle.LOW_SPEED_DIAL -> if (unit == SpeedUnit.KMH) 20 else 10
    else -> if (unit == SpeedUnit.KMH) 160 else 100
}

@Composable
internal fun DashboardSpeedDial(
    speed: Float?,
    formattedSpeed: String,
    unit: SpeedUnit,
    style: DashboardSpeedStyle,
    accent: Color
) {
    val colors = MaterialTheme.colorScheme
    val lowMaximum = speedDialMaximum(DashboardSpeedStyle.LOW_SPEED_DIAL, unit)
    var wasHigh by rememberSaveable(unit, style) { mutableStateOf(false) }
    val maximum = if (style == DashboardSpeedStyle.AUTO_DIAL) {
        val high = when {
            speed == null -> wasHigh
            wasHigh -> speed > lowMaximum * 0.9f
            else -> speed > lowMaximum
        }
        SideEffect { if (wasHigh != high) wasHigh = high }
        if (high) speedDialMaximum(DashboardSpeedStyle.DIAL, unit) else lowMaximum
    } else speedDialMaximum(style, unit)
    val fraction by key(unit, maximum) {
        animateFloatAsState(
            targetValue = speed?.let { (it / maximum).coerceIn(0f, 1f) } ?: 0f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f),
            label = "dashboard_speed_needle"
        )
    }
    val labelPaint = remember(colors.onSurfaceVariant) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onSurfaceVariant.toArgb()
            textAlign = Paint.Align.CENTER
        }
    }

    val range = stringResource(R.string.dashboard_speed_range, maximum, unit.label)
    Column(
        Modifier.fillMaxWidth().testTag("dashboard_speed_dial").semantics { stateDescription = range },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            Modifier.widthIn(max = 350.dp).fillMaxWidth().aspectRatio(1.90f)
                .testTag(if (speed != null) "dashboard_speed_dial_needle" else "dashboard_speed_dial_no_needle")
        ) {
            val center = Offset(size.width / 2f, size.height * 0.86f)
            val radius = minOf(size.width * 0.43f, size.height * 0.77f)
            fun polar(distance: Float, portion: Float): Offset {
                val angle = Math.PI * (1.0 + portion)
                return center + Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
            }

            val topLeft = center - Offset(radius, radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            val track = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(colors.surfaceContainerHighest, 180f, 180f, false, topLeft, arcSize, style = track)
            if (speed != null && fraction > 0f) {
                drawArc(accent, 180f, 180f * fraction, false, topLeft, arcSize, style = track)
            }

            labelPaint.textSize = 12.sp.toPx()
            val stepCount = 16
            for (step in 0..stepCount) {
                val portion = step.toFloat() / stepCount
                val major = step % (if (maximum == 10) 8 else 4) == 0
                val outer = polar(radius - 10.dp.toPx(), portion)
                val inner = polar(radius - (if (major) 20 else 16).dp.toPx(), portion)
                drawLine(colors.onSurfaceVariant, outer, inner, strokeWidth = (if (major) 2 else 1).dp.toPx())
                if (major) {
                    val textPosition = polar((radius - 33.dp.toPx()).coerceAtLeast(12.dp.toPx()), portion)
                    drawContext.canvas.nativeCanvas.drawText(
                        (maximum * portion).roundToInt().toString(),
                        textPosition.x, textPosition.y - (labelPaint.ascent() + labelPaint.descent()) / 2f, labelPaint
                    )
                }
            }

            if (speed != null) {
                drawLine(accent, center, polar(radius * 0.68f, fraction), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(accent, 5.dp.toPx(), center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formattedSpeed,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily, fontWeight = FontWeight.SemiBold),
                color = if (speed != null) colors.onSurface else colors.outline,
                modifier = Modifier.testTag("speed_readout_text")
            )
            Text(unit.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}
