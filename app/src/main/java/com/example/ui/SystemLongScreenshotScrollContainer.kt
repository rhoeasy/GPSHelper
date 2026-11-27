package com.example.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * Native Android [NestedScrollView] wrapper hosting a Compose content tree so Android 12+
 * ScrollCapture AND OEM scrolling-screenshot engines (Xiaomi HyperOS/MIUI, ColorOS, OriginOS,
 * OneUI, etc.) reliably detect the full scrollable range and capture native long screenshots.
 *
 * Also yields horizontal drags (e.g. scrubbing telemetry profile charts) cleanly to child
 * Compose pointer-input handlers without stealing the gesture.
 */
@Composable
fun SystemLongScreenshotScrollContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val parentComposition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            HorizontalFriendlyNestedScrollView(context).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
                isScrollbarFadingEnabled = true
                isNestedScrollingEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                clipToPadding = false

                val composeView = ComposeView(context).apply {
                    setParentCompositionContext(parentComposition)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        currentContent()
                    }
                }
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        },
        update = { scrollView ->
            val composeView = scrollView.getChildAt(0) as? ComposeView
            composeView?.setParentCompositionContext(parentComposition)
        }
    )
}

private class HorizontalFriendlyNestedScrollView(
    context: Context
) : NestedScrollView(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var isHorizontalGesture = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isHorizontalGesture = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (!isHorizontalGesture && dx > touchSlop && dx > dy * 1.15f) {
                    isHorizontalGesture = true
                }
                if (isHorizontalGesture) {
                    return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHorizontalGesture = false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
