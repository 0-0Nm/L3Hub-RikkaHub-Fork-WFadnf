package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/** Source-only here: run on the integrating Android target, not a substitute for device evidence. */
@RunWith(AndroidJUnit4::class)
class LiveGuidanceRowsTest {
    @get:Rule val rule = createComposeRule()
    private val calls = mutableListOf<Pair<String, Uuid>>()
    private fun pending(text: String = "pending") = LiveGuidance(content = text, runId = Uuid.random())

    private fun show(item: LiveGuidance, rtl: Boolean = false, busy: Boolean = false) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        LiveGuidancePanel(item, { _, _ -> }, { calls += "next" to it },
                            onCancel = { calls += "cancel" to it }, actionPending = busy)
                    }
                }
            }
        }
    }

    private fun drag(start: Float, end: Float) {
        rule.onNodeWithTag("live_guidance_card").performTouchInput {
            swipe(Offset(width * start, height / 2f), Offset(width * end, height / 2f), 500)
        }
    }

    @Test fun emptyListEmitsNoRowsOrEditor() {
        rule.setContent { MaterialTheme { LiveGuidanceRows(emptyList(), { _, _ -> }, {}, {}) } }
        rule.onNodeWithTag("live_guidance_rows").assertDoesNotExist()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun preservesCoreOrderIncludingUndeliveredAndDelivered() {
        val entries = listOf(pending("core first"), pending("core second").copy(delivery = LiveGuidanceDelivery.NOT_USED),
            pending("core third").copy(delivery = LiveGuidanceDelivery.DELIVERED))
        rule.setContent { MaterialTheme { LiveGuidanceRows(entries, { _, _ -> }, {}, {}) } }
        rule.onAllNodesWithTag("live_guidance_card").assertCountEquals(3)
        val tops = entries.map { rule.onNodeWithTag("live_guidance_row_${it.id}").fetchSemanticsNode().boundsInRoot.top }
        assertTrue(tops.zipWithNext().all { (first, second) -> first < second })
    }

    @Test fun laterRowsRemainReachableByVerticalScroll() {
        val entries = (1..12).map { pending("core $it") }
        rule.setContent { MaterialTheme { LiveGuidanceRows(entries, { _, _ -> }, {}, {}) } }
        rule.onNodeWithTag("live_guidance_row_${entries.last().id}").performScrollTo()
        rule.onNodeWithTag("live_guidance_row_${entries.last().id}").assertExists()
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
    }

    @Test fun physicalRightPartialIsClickableAndRetainsRowUntilServiceUpdate() {
        val item = pending(); show(item)
        drag(0.3f, 0.64f)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_swipe_cancel").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("cancel" to item.id), calls) }
        rule.onNodeWithTag("live_guidance_card").assertExists()
    }

    @Test fun physicalLeftPartialIsClickable() {
        val item = pending(); show(item)
        drag(0.7f, 0.36f)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_swipe_next").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf("next" to item.id), calls) }
    }

    @Test fun fullPhysicalRightFiresOnlyAfterUpAndOnce() {
        val item = pending(); show(item)
        rule.onNodeWithTag("live_guidance_card").performTouchInput {
            down(Offset(width * 0.12f, height / 2f))
            moveTo(Offset(width * 0.94f, height / 2f), 400)
        }
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_card").performTouchInput { up() }
        rule.runOnIdle { assertEquals(listOf("cancel" to item.id), calls) }
    }

    @Test fun fullPhysicalLeftFiresNextOnRelease() {
        val item = pending(); show(item)
        drag(0.88f, 0.06f)
        rule.runOnIdle { assertEquals(listOf("next" to item.id), calls) }
    }

    @Test fun rtlStillUsesPhysicalRightForCancel() {
        val item = pending(); show(item, rtl = true)
        drag(0.12f, 0.94f)
        rule.runOnIdle { assertEquals(listOf("cancel" to item.id), calls) }
    }

    @Test fun rtlStillUsesPhysicalLeftForNextMessage() {
        val item = pending(); show(item, rtl = true)
        drag(0.88f, 0.06f)
        rule.runOnIdle { assertEquals(listOf("next" to item.id), calls) }
    }

    @Test fun cancelledPointerBeyondThresholdDoesNotDispatch() {
        show(pending())
        rule.onNodeWithTag("live_guidance_card").performTouchInput {
            down(Offset(width * 0.12f, height / 2f))
            moveTo(Offset(width * 0.94f, height / 2f), 400)
            cancel()
        }
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
    }

    @Test fun verticalDragAndBothEdgesDoNotTriggerRowAction() {
        show(pending())
        rule.onNodeWithTag("live_guidance_card").performTouchInput {
            swipe(Offset(width / 2f, height * 0.9f), Offset(width / 2f + 2f, height * 0.1f), 500)
        }
        drag(0.01f, 0.95f)
        drag(0.99f, 0.05f)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_swipe_cancel").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_swipe_next").assertDoesNotExist()
    }

    @Test fun deliveredItemHasNoSwipeActionsButStillHasDetails() {
        val item = pending("delivered text").copy(delivery = LiveGuidanceDelivery.DELIVERED)
        show(item); drag(0.12f, 0.94f)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_card").assertExists()
        rule.onNodeWithTag("live_guidance_details").performClick()
        rule.onNodeWithTag("live_guidance_full_content").assertTextEquals(item.content)
        rule.onNodeWithTag("live_guidance_next").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_details_cancel").assertDoesNotExist()
    }

    @Test fun busyItemDisablesActionsWithoutHidingOriginalText() {
        val item = pending("retained text"); show(item, busy = true)
        drag(0.12f, 0.94f)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_mode").assertIsNotEnabled()
        rule.onNodeWithTag("live_guidance_details").performClick()
        rule.onNodeWithTag("live_guidance_full_content").assertTextEquals(item.content)
        rule.onNodeWithTag("live_guidance_details_cancel").assertIsNotEnabled()
        rule.onNodeWithTag("live_guidance_next").assertIsNotEnabled()
    }

    @Test fun accessibilityCancelTargetsExactItemWithoutSwipe() {
        val item = pending(); show(item)
        val actions = rule.onNodeWithTag("live_guidance_card").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle {
            assertEquals(2, actions.size)
            assertTrue(actions[0].action())
        }
        rule.runOnIdle { assertEquals(listOf("cancel" to item.id), calls) }
    }

    @Test fun accessibilityNextTargetsExactItemWithoutSwipe() {
        val item = pending(); show(item)
        val actions = rule.onNodeWithTag("live_guidance_card").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { assertTrue(actions[1].action()) }
        rule.runOnIdle { assertEquals(listOf("next" to item.id), calls) }
    }

    @Test fun detailsOffersFocusablePendingActions() {
        val item = pending(); show(item)
        rule.onNodeWithTag("live_guidance_details").performClick()
        rule.onNodeWithTag("live_guidance_details_cancel").assertExists()
        rule.onNodeWithTag("live_guidance_next").performClick()
        rule.runOnIdle { assertEquals(listOf("next" to item.id), calls) }
    }

    @Test fun keyedDetailsRemainAttachedToSameIdAfterListReorder() {
        val first = pending("first")
        val second = pending("second full text")
        val entries = mutableStateOf(listOf(first, second))
        rule.setContent { MaterialTheme { LiveGuidanceRows(entries.value, { _, _ -> }, {}, {}) } }
        rule.onAllNodesWithTag("live_guidance_details")[1].performClick()
        rule.runOnIdle { entries.value = listOf(second, first) }
        rule.onNodeWithTag("live_guidance_full_content").assertTextEquals(second.content)
    }

    @Test fun undeliveredHasNoSwipeButPreservesFullDetailsAndIdScopedRecovery() {
        val item = pending("original retained text ".repeat(30)).copy(
            delivery = LiveGuidanceDelivery.NOT_USED, finishLocked = true)
        show(item)
        drag(0.12f, 0.94f)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
        rule.onNodeWithTag("live_guidance_details").performClick()
        rule.onNodeWithTag("live_guidance_full_content").assertTextEquals(item.content)
        rule.onNodeWithTag("live_guidance_details_cancel").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_next").performClick()
        rule.runOnIdle { assertEquals(listOf("next" to item.id), calls) }
    }

    @Test fun verticalTouchOnRowStillScrollsTheLiveList() {
        val entries = (1..20).map { pending("entry $it") }
        rule.setContent { MaterialTheme { LiveGuidanceRows(entries, { _, _ -> }, {}, {}) } }
        val before = rule.onNodeWithTag("live_guidance_row_${entries.first().id}").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("live_guidance_rows").performTouchInput {
            swipe(Offset(width / 2f, height * 0.8f), Offset(width / 2f, height * 0.15f), 500)
        }
        val after = rule.onNodeWithTag("live_guidance_row_${entries.first().id}").fetchSemanticsNode().boundsInRoot.top
        assertTrue(after < before)
        rule.runOnIdle { assertTrue(calls.isEmpty()) }
    }


    @Test fun archivedHistoryKeepsReadOnlyFullDetails() {
        val entry = ArchivedLiveGuidance(
            id = Uuid.random(), runId = Uuid.random(), content = "archived original text ".repeat(30),
            mode = LiveGuidanceMode.PERSISTENT, nodeId = Uuid.random(), messageId = Uuid.random(),
        )
        rule.setContent { MaterialTheme { ArchivedLiveGuidanceCard(entry) } }
        rule.onNodeWithTag("archived_live_guidance_details").performClick()
        rule.onNodeWithTag("archived_live_guidance_full_content").assertTextEquals(entry.content)
        rule.onNodeWithTag("live_guidance_next").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_details_cancel").assertDoesNotExist()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

}
