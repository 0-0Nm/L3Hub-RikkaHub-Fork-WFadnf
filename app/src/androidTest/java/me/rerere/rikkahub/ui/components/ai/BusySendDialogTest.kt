package me.rerere.rikkahub.ui.components.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsAtMost
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.hooks.ComposerSendSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/** Cloud/device tests. No fake service or database implementation: exercise UI callback isolation. */
@RunWith(AndroidJUnit4::class)
class BusySendDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun showChooser(attachmentsOnly: Boolean = false, submitting: Boolean = false): ComposerSendSnapshot {
        val input = ChatInputState().apply {
            if (!attachmentsOnly) setMessageText("submitted text")
            messageContent = listOf(UIMessagePart.Image("file:///keep.png"))
        }
        val snapshot = input.captureSendSnapshot(Uuid.random())
        rule.setContent {
            MaterialTheme {
                BusySendDialog(
                    snapshot = snapshot,
                    submitting = submitting,
                    onDismiss = { calls += "dismiss" },
                    onQueue = { calls += "queue" },
                    onGuide = { persistent -> calls += "guide:$persistent" },
                    onLegacyInterrupt = { calls += "interrupt" },
                )
            }
        }
        return snapshot
    }

    @Test
    fun queueDoesNotCallInterruptOrGuidance() {
        showChooser()
        rule.onNodeWithTag("busy_send_queue").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("queue"), calls) }
    }

    @Test
    fun temporaryGuidanceIsNonpersistentAndDoesNotCallInterrupt() {
        showChooser()
        rule.onNodeWithTag("busy_send_temporary").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("guide:false"), calls) }
    }

    @Test
    fun persistentGuidanceUsesTheSameCallbackWithExplicitTrue() {
        showChooser()
        rule.onNodeWithTag("busy_send_persistent").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("guide:true"), calls) }
    }

    @Test
    fun legacyInterruptIsExplicitAndSeparate() {
        showChooser()
        rule.onNodeWithTag("busy_send_legacy_interrupt").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("interrupt"), calls) }
    }

    @Test
    fun attachmentsAloneDisableOnlyGuidance() {
        showChooser(attachmentsOnly = true)
        rule.onNodeWithTag("busy_send_queue").assertIsEnabled()
        rule.onNodeWithTag("busy_send_temporary").assertIsNotEnabled()
        rule.onNodeWithTag("busy_send_persistent").assertIsNotEnabled()
        rule.onNodeWithTag("busy_send_legacy_interrupt").assertIsEnabled()
    }

    @Test
    fun acceptanceInFlightDisablesAllSendChoices() {
        showChooser(submitting = true)
        listOf("queue", "temporary", "persistent", "legacy_interrupt").forEach {
            rule.onNodeWithTag("busy_send_$it").assertIsNotEnabled()
        }
        rule.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    @Test
    fun cancelNeverRoutesToAnySendAction() {
        showChooser()
        rule.onNodeWithTag("busy_send_cancel").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("dismiss"), calls) }
    }

    @Test
    fun absentRecordEmitsNoCardOrEditor() {
        rule.setContent {
            MaterialTheme { LiveGuidancePanel(null, { _, _ -> }, {}) }
        }
        rule.onNodeWithTag("live_guidance_card").assertDoesNotExist()
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun cardIsCompactAndHasNoSecondEditorOrEagerDetails() {
        rule.setContent {
            MaterialTheme { LiveGuidancePanel(LiveGuidance(content = "retained"), { _, _ -> }, {}) }
        }
        rule.onNodeWithTag("live_guidance_card").assertHeightIsAtMost(64.dp)
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        rule.onNodeWithTag("live_guidance_full_content").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_next").assertDoesNotExist()
    }

    @Test
    fun modeSwitchUsesSameIdWithoutOptimisticModelChange() {
        val item = LiveGuidance(content = "guide", runId = Uuid.random())
        var modeCall: Pair<Uuid, Boolean>? = null
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(item, { id, persistent -> modeCall = id to persistent }, {})
            }
        }
        rule.onNodeWithTag("live_guidance_mode").performClick()
        rule.runOnIdle { assertEquals(item.id to true, modeCall) }
        rule.onNodeWithTag("live_guidance_mode").assertIsOff()
    }

    @Test
    fun serviceOwnedModeCanSwitchBothDirectionsOnSameItem() {
        val item = mutableStateOf(LiveGuidance(content = "guide", runId = Uuid.random()))
        val id = item.value.id
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(item.value, { cardId, persistent ->
                    calls += cardId.toString()
                    item.value = item.value.withPersistence(persistent, item.value.runId)
                }, {})
            }
        }
        rule.onNodeWithTag("live_guidance_mode").performClick().assertIsOn()
        rule.onNodeWithTag("live_guidance_mode").performClick().assertIsOff()
        rule.runOnIdle { assertEquals(listOf(id.toString(), id.toString()), calls) }
    }

    @Test
    fun missedItemRetainsFullTextAndExposesOnlySafeRecoveryOnDemand() {
        val content = "full original guidance ".repeat(50)
        val item = LiveGuidance(content = content, delivery = LiveGuidanceDelivery.NOT_USED, finishLocked = true)
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(item, { _, _ -> calls += "mode" }, { calls += "next" })
            }
        }
        rule.onNodeWithTag("live_guidance_mode").assertIsNotEnabled()
        rule.onNodeWithTag("live_guidance_next").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_details").performClick()
        rule.onNodeWithTag("live_guidance_full_content").assertTextEquals(content)
        rule.onNodeWithTag("live_guidance_retry").assertDoesNotExist()
        rule.onNodeWithTag("live_guidance_next").performClick()
        rule.runOnIdle { assertEquals(listOf("next"), calls) }
    }

    @Test
    fun newItemClosesOldDetails() {
        val item = mutableStateOf(LiveGuidance(content = "first"))
        rule.setContent {
            MaterialTheme { LiveGuidancePanel(item.value, { _, _ -> }, {}) }
        }
        rule.onNodeWithTag("live_guidance_details").performClick()
        rule.runOnIdle { item.value = LiveGuidance(content = "second") }
        rule.onNodeWithTag("live_guidance_full_content").assertDoesNotExist()
    }

    @Test
    fun mainSendSupportsSeparateShortAndLongGestures() {
        rule.setContent {
            MaterialTheme {
                SendButton(loading = true, empty = false, busyInput = true,
                    onClick = { calls += "short" }, onLongClick = { calls += "long" })
            }
        }
        rule.onNodeWithTag("chat_send_button").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        rule.onNodeWithTag("chat_send_button").performClick()
        rule.onNodeWithTag("chat_send_button").performTouchInput { longClick() }
        rule.runOnIdle { assertEquals(listOf("short", "long"), calls) }
    }
}
