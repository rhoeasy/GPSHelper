package com.example.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.track.SocialCardConfig
import com.example.track.SocialLayerPlacement
import com.example.track.SocialPosterEdits
import com.example.track.SocialPosterLayer
import com.example.ui.theme.GpsHudTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SocialPhotoPosterPreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun draggingTheRouteChangesOnlyItsPosition() {
        val route = SocialPosterLayer.ROUTE
        val data = SocialPosterLayer.DATA
        val initialRoute = SocialLayerPlacement(0.20f, 0.20f, 0.6f)
        val initialData = SocialLayerPlacement(0.9f, 0.9f)
        val config = mutableStateOf(SocialCardConfig(posterEdits = SocialPosterEdits(route = initialRoute, data = initialData)))
        val photo = Bitmap.createBitmap(280, 280, Bitmap.Config.ARGB_8888)
        val artwork = mapOf(
            route to Bitmap.createBitmap(120, 130, Bitmap.Config.ARGB_8888),
            data to Bitmap.createBitmap(220, 80, Bitmap.Config.ARGB_8888)
        )

        composeRule.setContent {
            GpsHudTheme {
                SocialPhotoPosterPreview(photo, artwork, config.value, route, {}) { layer, placement ->
                    config.value = config.value.copy(posterEdits = config.value.posterEdits.place(layer, placement))
                }
            }
        }

        composeRule.onNodeWithTag("social_poster_selected_layer").performTouchInput {
            swipe(center, center + Offset(35f, 45f))
        }
        composeRule.runOnIdle {
            val moved = requireNotNull(config.value.posterEdits.route)
            assertTrue(moved.x > initialRoute.x)
            assertTrue(moved.y > initialRoute.y)
            assertEquals(initialRoute.scale, moved.scale, 0f)
            assertEquals(initialData, config.value.posterEdits.data)
        }
    }
}
