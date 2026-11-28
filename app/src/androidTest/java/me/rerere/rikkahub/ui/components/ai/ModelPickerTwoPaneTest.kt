package me.rerere.rikkahub.ui.components.ai

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Source-only E352 coverage. Execute on the integration device; not run in this UI lane. */
@RunWith(AndroidJUnit4::class)
class ModelPickerTwoPaneTest {
    @get:Rule val rule = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun providerButton(initial: String) = rule.onNode(
        hasClickAction() and hasText(initial),
    )

    // Empty model lists keep these layout tests independent of navigation/data/core.
    private fun show(
        providers: List<ProviderSetting>,
        width: Int = 320,
        fontScale: Float = 1f,
        onDismiss: () -> Unit = {},
    ) {
        rule.setContent {
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = width }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalConfiguration provides configuration,
            ) {
                MaterialTheme {
                    Box(Modifier.requiredWidth(width.dp).height(560.dp)) {
                        ModelPickerTwoPane(
                            currentModel = null,
                            providers = providers,
                            modelType = ModelType.CHAT,
                            groups = emptyList(),
                            assignments = emptyMap(),
                            favoriteIds = emptyList(),
                            onSelect = {},
                            onReorderGroups = {},
                            onToggleFavorite = {},
                            onDismiss = onDismiss,
                            onOpenProvider = {},
                            onEditModel = { _, _ -> },
                            onOpenProviderSettings = {},
                        )
                    }
                }
            }
        }
    }

    @Test fun smallPortraitSwitchesFullProviderNameWithoutChangingSearchOrClose() {
        val first = ProviderSetting.OpenAI(name = "北方 / 自定义供应商完整名称")
        val second = ProviderSetting.OpenAI(name = "南方 / 另一家供应商完整名称")
        var dismissed = false
        show(listOf(first, second), onDismiss = { dismissed = true })
        rule.onNodeWithText(first.name).assertIsDisplayed()
        providerButton("南").performClick()
        rule.onNodeWithText(second.name).assertIsDisplayed()
        rule.onNodeWithText(first.name).assertDoesNotExist()
        rule.onNode(hasSetTextAction()).performTextInput("not-a-match")
        rule.onNodeWithText(second.name).assertIsDisplayed() // Search does not change rail highlight.
        rule.onNodeWithText(text(R.string.model_list_no_match)).assertIsDisplayed()
        providerButton("北").performClick() // Existing behavior clears search on provider selection.
        rule.onNodeWithText(first.name).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.model_list_no_match)).assertDoesNotExist()
        rule.onNodeWithContentDescription(text(R.string.close)).performClick()
        rule.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun longNameAt360DpAndLargeFontWrapsWithoutEllipsisAndRemainsScrollable() {
        val name = "北方供应商 / " + "完整显示名称LongConfiguredName-".repeat(30)
        show(listOf(ProviderSetting.OpenAI(name = name)), width = 360, fontScale = 2f)
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(name, layout.layoutInput.text.text)
        assertTrue(layout.lineCount > 1)
        assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
        var scrolled = false
        rule.onNodeWithText(name).performSemanticsAction(SemanticsActions.ScrollBy) { scrolled = it(0f, 10000f) }
        rule.runOnIdle { assertTrue(scrolled) }
        rule.onNode(hasSetTextAction()).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.close)).assertIsDisplayed()
        rule.onNodeWithContentDescription(text(R.string.model_list_collapse_sidebar)).performClick()
        rule.onNodeWithContentDescription(text(R.string.model_list_expand_sidebar)).assertIsDisplayed().performClick()
        providerButton("北").assertIsDisplayed()
    }

    @Test fun providerListScrollsAndRailControlsKeep48DpTargetsAt320Dp() {
        val providers = (0..35).map { index ->
            ProviderSetting.OpenAI(name = "${('北'.code + index).toChar()} 自定义供应商 $index")
        }
        show(providers)
        val button = providerButton("北").fetchSemanticsNode().boundsInRoot
        val group = rule.onNodeWithText(text(R.string.setting_provider_group_none)).fetchSemanticsNode().boundsInRoot
        val collapse = rule.onNodeWithContentDescription(text(R.string.model_list_collapse_sidebar))
            .fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithContentDescription(text(R.string.setting_provider_page_title))
            .fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            with(rule.density) {
                assertEquals(64.dp.toPx(), group.width, 1f)
                listOf(button, collapse, settings, group).forEach {
                    assertTrue(it.width >= 48.dp.toPx() - 1f)
                    assertTrue(it.height >= 48.dp.toPx() - 1f)
                }
            }
        }
        // Sidebar is the first LazyColumn; one group label precedes the provider entries.
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(providers.size)
        val last = providers.last()
        providerButton(last.name.take(1)).assertIsDisplayed().performClick()
        rule.onNodeWithText(last.name).assertIsDisplayed()
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(0)
        providerButton("北").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.setting_provider_group_none)).performClick()
        providerButton("北").assertDoesNotExist()
        rule.onNodeWithText(text(R.string.setting_provider_group_none)).performClick()
        providerButton("北").assertIsDisplayed()
    }

    @Test fun renamedHighlightedProviderRecomposesAndEmptyProvidersKeepLocalizedFallback() {
        val providers = mutableStateOf<List<ProviderSetting>>(listOf(ProviderSetting.OpenAI(name = "北方原名称")))
        rule.setContent {
            MaterialTheme {
                Box(Modifier.requiredWidth(320.dp).height(560.dp)) {
                    ModelPickerTwoPane(
                        currentModel = null, providers = providers.value, modelType = ModelType.CHAT,
                        groups = emptyList(), assignments = emptyMap(), favoriteIds = emptyList(),
                        onSelect = {}, onReorderGroups = {}, onToggleFavorite = {}, onDismiss = {},
                        onOpenProvider = {}, onEditModel = { _, _ -> }, onOpenProviderSettings = {},
                    )
                }
            }
        }
        rule.onNodeWithText("北方原名称").assertIsDisplayed()
        rule.runOnIdle { providers.value = listOf(providers.value.single().copyProvider(name = "北方新名称")) }
        rule.onNodeWithText("北方新名称").assertIsDisplayed()
        rule.runOnIdle { providers.value = emptyList() }
        rule.onNodeWithText(text(R.string.model_list_select_model)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.model_list_no_providers)).assertIsDisplayed()
    }
}
