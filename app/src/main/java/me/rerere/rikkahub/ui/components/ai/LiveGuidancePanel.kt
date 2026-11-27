package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceDelivery
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import kotlin.uuid.Uuid

/** Recorded service-owned item only. Null emits no layout node; the main composer is the only editor. */
@Composable
fun LiveGuidancePanel(
    guidance: LiveGuidance?,
    onModeChange: (Uuid, Boolean) -> Unit,
    onNextMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = guidance ?: return
    var showDetails by remember(item.id) { mutableStateOf(false) }
    val persistent = item.mode == LiveGuidanceMode.PERSISTENT
    val canChangeMode = item.runId != null && !item.finishLocked &&
        item.delivery != LiveGuidanceDelivery.NOT_USED
    val modeLabel = stringResource(
        if (persistent) R.string.chat_page_busy_persistent else R.string.chat_page_busy_temporary
    )
    val statusLabel = stringResource(
        when (item.delivery) {
            LiveGuidanceDelivery.WAITING -> R.string.chat_page_guidance_compact_waiting
            LiveGuidanceDelivery.DELIVERED -> R.string.chat_page_guidance_compact_delivered
            LiveGuidanceDelivery.NOT_USED -> R.string.chat_page_guidance_compact_not_used
        }
    )
    Surface(
        modifier = modifier.fillMaxWidth().testTag("live_guidance_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = persistent,
                        enabled = canChangeMode,
                        role = Role.Switch,
                        onValueChange = { target -> onModeChange(item.id, target) },
                    )
                    .testTag("live_guidance_mode")
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "$modeLabel · $statusLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(item.content, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(
                onClick = { showDetails = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("live_guidance_details"),
            ) { Text(stringResource(R.string.chat_page_guidance_details)) }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(modeLabel) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(when (item.delivery) {
                        LiveGuidanceDelivery.WAITING -> R.string.chat_page_live_guidance_waiting
                        LiveGuidanceDelivery.DELIVERED -> R.string.chat_page_live_guidance_delivered
                        LiveGuidanceDelivery.NOT_USED -> R.string.chat_page_live_guidance_not_used
                    }))
                    Text(item.content, modifier = Modifier.testTag("live_guidance_full_content"))
                    Text(stringResource(R.string.chat_page_guidance_mode_hint))
                }
            },
            confirmButton = {
                if (item.delivery == LiveGuidanceDelivery.NOT_USED) {
                    // Safe fallback only. The backend retry/queue-lock risk is NOT fixed here.
                    TextButton(
                        onClick = { onNextMessage(); showDetails = false },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("live_guidance_next"),
                    ) { Text(stringResource(R.string.chat_page_busy_queue)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.chat_page_guidance_close))
                }
            },
        )
    }
}

/**
 * Durable run-finish history entry. Read-only: the mode was locked with its originating run, and a
 * delivered note has no next-message recovery. It lives in the scrollable history, not in the
 * composer and not as a model/user message.
 */
@Composable
fun ArchivedLiveGuidanceCard(
    entry: me.rerere.rikkahub.data.model.ArchivedLiveGuidance,
    modifier: Modifier = Modifier,
) {
    var showDetails by remember(entry.id) { mutableStateOf(false) }
    val persistent = entry.mode == LiveGuidanceMode.PERSISTENT
    val modeLabel = stringResource(
        if (persistent) R.string.chat_page_busy_persistent else R.string.chat_page_busy_temporary
    )
    Surface(
        modifier = modifier.fillMaxWidth().testTag("archived_live_guidance_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_page_guidance_archived) + " · " + modeLabel +
                        " · " + stringResource(R.string.chat_page_guidance_compact_delivered),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { showDetails = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("archived_live_guidance_details"),
            ) { Text(stringResource(R.string.chat_page_guidance_details)) }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.chat_page_guidance_archived)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.chat_page_live_guidance_delivered))
                    Text(entry.content, modifier = Modifier.testTag("archived_live_guidance_full_content"))
                    Text(
                        stringResource(
                            if (persistent) {
                                R.string.chat_page_guidance_archived_persistent_hint
                            } else {
                                R.string.chat_page_guidance_archived_temporary_hint
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.chat_page_guidance_close))
                }
            },
        )
    }
}
