package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.ComposerSendSnapshot

/** Explicit busy-send choices. Only the separately labeled legacy action calls interrupt. */
@Composable
internal fun BusySendDialog(
    snapshot: ComposerSendSnapshot,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onQueue: () -> Unit,
    onGuide: (persistent: Boolean) -> Unit,
    onLegacyInterrupt: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!submitting) onDismiss() }) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.chat_page_busy_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.chat_page_busy_description), style = MaterialTheme.typography.bodyMedium)
                if (snapshot.text.isNotBlank()) {
                    Text(snapshot.text.take(400), maxLines = 4, style = MaterialTheme.typography.bodySmall)
                }
                if (snapshot.attachments.isNotEmpty()) {
                    Text(stringResource(R.string.chat_page_busy_attachments), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = onQueue,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("busy_send_queue"),
                ) { Text(stringResource(R.string.chat_page_busy_queue)) }
                Text(stringResource(R.string.chat_page_busy_queue_description), style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(
                    onClick = { onGuide(false) },
                    enabled = !submitting && snapshot.canGuide,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("busy_send_temporary"),
                ) { Text(stringResource(R.string.chat_page_busy_temporary)) }
                Text(stringResource(R.string.chat_page_busy_temporary_description), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { onGuide(true) },
                    enabled = !submitting && snapshot.canGuide,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("busy_send_persistent"),
                ) { Text(stringResource(R.string.chat_page_busy_persistent)) }
                Text(stringResource(R.string.chat_page_busy_persistent_description), style = MaterialTheme.typography.bodySmall)
                if (!snapshot.canGuide) {
                    Text(stringResource(R.string.chat_page_busy_text_required), style = MaterialTheme.typography.bodySmall)
                }
                if (submitting) {
                    Text(stringResource(R.string.chat_page_busy_submitting), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
                // Deliberately separate, explicitly destructive legacy behavior; never the default.
                TextButton(
                    onClick = onLegacyInterrupt,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("busy_send_legacy_interrupt"),
                ) {
                    Text(stringResource(R.string.chat_page_busy_legacy_interrupt), color = MaterialTheme.colorScheme.error)
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("busy_send_cancel"),
                ) { Text(stringResource(R.string.chat_page_busy_cancel)) }
            }
        }
    }
}
