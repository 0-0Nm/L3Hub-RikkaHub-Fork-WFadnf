package me.rerere.rikkahub.ui.components.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
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
import org.junit.Assert.assertNotNull
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
    fun panelDelayedAcceptanceKeepsNewerDraft() {
        val draft = mutableStateOf("old draft")
        var accepted: (() -> Unit)? = null
        val owner = Uuid.random()
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(
                    guidance = null,
                    draft = draft.value,
                    draftOwner = owner,
                    onDraftChange = { draft.value = it },
                    onSubmit = { _, callback -> accepted = callback },
                    onModeChange = { _, _ -> },
                    onNextMessage = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag("live_guidance_submit").performScrollTo().performClick()
        rule.runOnIdle { assertNotNull(accepted); draft.value = "new draft" }
        rule.waitForIdle()
        rule.runOnIdle { accepted!!(); assertEquals("new draft", draft.value) }
    }

    @Test
    fun panelDelayedAcceptanceKeepsChangedConversationDraft() {
        val draft = mutableStateOf("same text")
        val owner = mutableStateOf(Uuid.random())
        var accepted: (() -> Unit)? = null
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(
                    guidance = null,
                    draft = draft.value,
                    draftOwner = owner.value,
                    onDraftChange = { draft.value = it },
                    onSubmit = { _, callback -> accepted = callback },
                    onModeChange = { _, _ -> },
                    onNextMessage = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag("live_guidance_submit").performScrollTo().performClick()
        rule.runOnIdle { owner.value = Uuid.random() }
        rule.waitForIdle()
        rule.runOnIdle { accepted!!(); assertEquals("same text", draft.value) }
    }

    @Test
    fun cardModeUsesTheExistingIdAndWaitsForServiceOwnedState() {
        val id = Uuid.random()
        val item = LiveGuidance(id = id, content = "guide", runId = Uuid.random())
        var modeCall: Pair<Uuid, Boolean>? = null
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(
                    guidance = item,
                    draft = "",
                    onDraftChange = {},
                    onSubmit = { _, _ -> },
                    onModeChange = { cardId, persistent -> modeCall = cardId to persistent },
                    onNextMessage = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag("live_guidance_mode").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(id to true, modeCall) }
        rule.onNodeWithTag("live_guidance_mode").assertIsOff()
    }

    @Test
    fun recoveryButtonsOnlyInvokeTheirExistingCallbacks() {
        val item = LiveGuidance(content = "missed", delivery = LiveGuidanceDelivery.NOT_USED, finishLocked = true)
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(
                    guidance = item,
                    draft = "",
                    onDraftChange = {},
                    onSubmit = { _, _ -> },
                    onModeChange = { _, _ -> calls += "mode" },
                    onNextMessage = { calls += "next" },
                    onRetry = { calls += "retry" },
                )
            }
        }
        rule.onNodeWithTag("live_guidance_mode").assertIsNotEnabled()
        rule.onNodeWithTag("live_guidance_next").performScrollTo().performClick()
        rule.onNodeWithTag("live_guidance_retry").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("next", "retry"), calls) }
    }

    @Test
    fun cancelNeverRoutesToAnySendAction() {
        showChooser()
        rule.onNodeWithTag("busy_send_cancel").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("dismiss"), calls) }
    }

    @Test
    fun panelUnchangedAcceptedDraftCanClear() {
        val draft = mutableStateOf("accepted draft")
        var accepted: (() -> Unit)? = null
        rule.setContent {
            MaterialTheme {
                LiveGuidancePanel(
                    guidance = null,
                    draft = draft.value,
                    onDraftChange = { draft.value = it },
                    onSubmit = { _, callback -> accepted = callback },
                    onModeChange = { _, _ -> },
                    onNextMessage = {},
                    onRetry = {},
                )
            }
        }
        rule.onNodeWithTag("live_guidance_submit").performScrollTo().performClick()
        rule.runOnIdle { accepted!!(); assertEquals("", draft.value) }
    }

    @Test
    fun disposedPanelDoesNotClearItsOldDraft() {
        val show = mutableStateOf(true)
        val draft = mutableStateOf("retained")
        var accepted: (() -> Unit)? = null
        rule.setContent {
            MaterialTheme {
                if (show.value) {
                    LiveGuidancePanel(
                        guidance = null,
                        draft = draft.value,
                        onDraftChange = { draft.value = it },
                        onSubmit = { _, callback -> accepted = callback },
                        onModeChange = { _, _ -> },
                        onNextMessage = {},
                        onRetry = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("live_guidance_submit").performScrollTo().performClick()
        rule.runOnIdle { show.value = false }
        rule.waitForIdle()
        rule.runOnIdle { accepted!!(); assertEquals("retained", draft.value) }
    }
}
