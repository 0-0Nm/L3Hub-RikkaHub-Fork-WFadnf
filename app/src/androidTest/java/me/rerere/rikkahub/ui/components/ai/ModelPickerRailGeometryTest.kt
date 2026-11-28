package me.rerere.rikkahub.ui.components.ai

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.rikkahub.R
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** E353 native layout assertions. Added as source; not run in the offline UI lane. */
@RunWith(AndroidJUnit4::class)
class ModelPickerRailGeometryTest {
    @get:Rule val rule = createComposeRule()

    private fun checkActualLane(width: Int, fontScale: Float = 1f) {
        // requiredWidth alone can overflow/clamp on a smaller real display and
        // yield clipped bounds rather than an honest 840dp viewport. Skip there.
        val available = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.screenWidthDp
        assumeTrue("Requires a real native window at least ${width}dp wide", available >= width)
        // Deliberately synthetic fixture: no customer data or network dependency.
        val model = Model(modelId = "geometry-fixture", displayName = "Geometry fixture")
        val provider = ProviderSetting.OpenAI(name = "北方 / 完整供应商名称", models = listOf(model))
        rule.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = width }
            val navigator = remember { Navigator(mutableListOf()) }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalConfiguration provides configuration,
                LocalNavController provides navigator,
                LocalLayoutDirection provides LayoutDirection.Ltr,
            ) {
                MaterialTheme {
                    // A is the visible container edge BEFORE the production outer inset.
                    Box(Modifier.requiredWidth(width.dp).height(560.dp).testTag("picker_viewport")) {
                        Box(Modifier.modelPickerContentInsets().fillMaxSize()) {
                            ModelPickerTwoPane(
                                currentModel = model.id, providers = listOf(provider), modelType = ModelType.CHAT,
                                groups = emptyList(), assignments = emptyMap(), favoriteIds = emptyList(),
                                onSelect = {}, onReorderGroups = {}, onToggleFavorite = {}, onDismiss = {},
                                onOpenProvider = {}, onEditModel = { _, _ -> }, onOpenProviderSettings = {},
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle() // Includes the rail's AnimatedVisibility settling.
        val viewport = rule.onNodeWithTag("picker_viewport").fetchSemanticsNode().boundsInRoot
        // Semantics is on the clickable 48dp Surface, NOT its 4dp padding wrapper
        // or the smaller AutoAIIcon artwork inside it.
        val tile = rule.onNode(hasClickAction() and hasText("北")).fetchSemanticsNode().boundsInRoot
        val divider = rule.onNodeWithTag("model_picker_divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val card = rule.onAllNodesWithTag("model_picker_card", useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            // D convention for ALL three gaps: D = divider START edge (D-left).
            // DE therefore includes the 1dp stroke; the clear gap is checked apart.
            val ab = tile.left - viewport.left
            val cd = divider.left - tile.right
            val de = card.left - divider.left
            val clearDe = card.left - divider.right
            // Each edge is independently pixel-rounded: allow 1px per compared pair.
            assertEquals("A-B must equal C-D", ab, cd, 1f)
            assertEquals("C-D must equal D-E", cd, de, 1f)
            assertEquals("A-B must equal D-E", ab, de, 1f)
            assertEquals("A-B compressed to 7dp", 7.dp.toPx(), ab, 1f)
            assertEquals("C-D compressed to 7dp", 7.dp.toPx(), cd, 1f)
            assertEquals("D-E unchanged from E352 (7dp incl. stroke)", 7.dp.toPx(), de, 1f)
            assertEquals("Divider stroke stays 1dp", 1.dp.toPx(), divider.width, 1f)
            assertEquals("Clear D-right to E stays 6dp", 6.dp.toPx(), clearDe, 1f)
            assertEquals(62.dp.toPx(), divider.left - viewport.left, 1f)
            assertEquals(48.dp.toPx(), tile.width, 1f)
            assertEquals(tile.width, tile.height, 1f)
            assertTrue(tile.width >= 48.dp.toPx() - 1f && tile.height >= 48.dp.toPx() - 1f)
            assertEquals(69.dp.toPx(), card.left - viewport.left, 2f)
        }
        rule.onNodeWithText(provider.name).assertIsDisplayed()
        rule.onNodeWithText(model.displayName).assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        rule.onNodeWithContentDescription(context.getString(R.string.model_list_collapse_sidebar)).performClick()
        rule.waitForIdle()
        val collapsedCard = rule.onAllNodesWithTag("model_picker_card", useUnmergedTree = true)[0]
            .fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            // E352 collapsed: outer start 8dp + collapsed list inset 12dp.
            assertEquals(20.dp.toPx(), collapsedCard.left - viewport.left, 2f)
        }
        rule.onNodeWithContentDescription(context.getString(R.string.model_list_expand_sidebar)).performClick()
        rule.waitForIdle()
        val reopenedDivider = rule.onNodeWithTag("model_picker_divider", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertEquals(62.dp.toPx(), reopenedDivider.left - viewport.left, 1f)
        }
    }

    @Test fun actualOuterLaneAt320Dp() = checkActualLane(320)
    @Test fun actualOuterLaneAt360DpAndLargeFont() = checkActualLane(360, fontScale = 2f)
    @Test fun actualOuterLaneAt393Dp() = checkActualLane(393)
    @Test fun actualOuterLaneAt840Dp() = checkActualLane(840)
}
