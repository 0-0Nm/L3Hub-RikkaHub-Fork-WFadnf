package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import kotlin.uuid.Uuid

/** Pure-text draft and a model-owned card; callbacks never create chat messages here. */
@Composable
fun LiveGuidancePanel(
    guidance: LiveGuidance?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: (String, () -> Unit) -> Unit,
    onModeChange: (Uuid, Boolean) -> Unit,
    onNextMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    draftOwner: Uuid? = null,
) {
    val latestDraft by rememberUpdatedState(draft)
    val latestDraftOwner by rememberUpdatedState(draftOwner)
    val ownerActive = remember(draftOwner) { mutableStateOf(true) }
    DisposableEffect(draftOwner) {
        onDispose { ownerActive.value = false }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_page_live_guidance_title),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                label = { Text(stringResource(R.string.chat_page_live_guidance_placeholder)) },
                minLines = 2,
                maxLines = 3,
            )
            FilledTonalButton(
                onClick = {
                    val submittedDraft = draft
                    val submittedOwner = draftOwner
                    if (submittedDraft.isNotBlank()) {
                        onSubmit(submittedDraft) {
                            // A delayed acceptance must not erase a newly edited draft.
                            if (ownerActive.value && latestDraftOwner == submittedOwner && latestDraft == submittedDraft) {
                                onDraftChange("")
                            }
                        }
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = 48.dp)
                    .testTag("live_guidance_submit"),
            ) {
                Text(stringResource(R.string.chat_page_live_guidance_submit))
            }

            // Keep terminal cards and their last mode visible. Only the owner replaces a card.
            guidance?.let { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                when (item.delivery) {
                                    LiveGuidanceDelivery.WAITING -> R.string.chat_page_live_guidance_waiting
                                    LiveGuidanceDelivery.DELIVERED -> R.string.chat_page_live_guidance_delivered
                                    LiveGuidanceDelivery.NOT_USED -> R.string.chat_page_live_guidance_not_used
                                }
                            ),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val persistentLabel = stringResource(R.string.chat_page_live_guidance_persistent)
                        val canChangeMode = item.runId != null &&
                            !item.finishLocked && item.delivery != LiveGuidanceDelivery.NOT_USED
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = persistentLabel,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = item.mode == LiveGuidanceMode.PERSISTENT,
                                onCheckedChange = { persistent ->
                                    if (canChangeMode) onModeChange(item.id, persistent)
                                },
                                enabled = canChangeMode,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = persistentLabel }
                                    .testTag("live_guidance_mode"),
                            )
                        }
                        if (item.delivery == LiveGuidanceDelivery.NOT_USED) {
                            FilledTonalButton(
                                onClick = onNextMessage,
                                modifier = Modifier.testTag("live_guidance_next")
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.chat_page_live_guidance_next))
                            }
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.testTag("live_guidance_retry")
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.chat_page_live_guidance_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}
