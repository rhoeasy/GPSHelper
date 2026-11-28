package com.example.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
internal fun Modifier.mainTabSwipe(activeTab: MainTab, enabled: Boolean, onSelectTab: (MainTab) -> Unit): Modifier {
    val select by rememberUpdatedState(onSelectTab)
    if (!enabled) return this
    return pointerInput(activeTab) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = {
                if (abs(distance) >= 64.dp.toPx()) {
                    MainTab.entries.getOrNull(activeTab.ordinal + if (distance < 0) 1 else -1)?.let(select)
                }
            }
        ) { change, drag ->
            change.consume()
            distance += drag
        }
    }
}
