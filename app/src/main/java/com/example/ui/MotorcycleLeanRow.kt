package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.track.RecordingSnapshot
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
internal fun motorcycleLeanText(degrees: Float?): String = when (val angle = degrees?.roundToInt()) {
    null -> "--"
    0 -> "0°"
    in 1..180 -> stringResource(R.string.moto_lean_right, angle)
    else -> stringResource(R.string.moto_lean_left, -angle)
}

@Composable
internal fun MotorcycleLeanRow(snapshot: RecordingSnapshot, onZero: () -> Unit) {
    if (!snapshot.showMotorcycleLean) return
    val reading = snapshot.motorcycleLean
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(cs.surfaceContainer).padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("motorcycle_lean_dashboard"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.moto_lean), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(
                if (reading.sensorAvailable) motorcycleLeanText(reading.degrees)
                else stringResource(R.string.moto_lean_sensor_missing),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = cs.onSurface
            )
        }
        if (reading.sensorAvailable) {
            TextButton(enabled = reading.canZero, onClick = onZero) { Text(stringResource(R.string.moto_lean_zero)) }
        }
    }
}
