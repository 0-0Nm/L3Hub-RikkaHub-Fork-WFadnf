package me.rerere.rikkahub.ui.pages.log

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpDiagnostics
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpProbe
import me.rerere.rikkahub.data.ai.mcp.McpProbeRules.Hint
import me.rerere.rikkahub.data.ai.mcp.serverUrl
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun McpProbeCard() {
    val manager = koinInject<McpManager>()
    val servers by manager.diagnosticServers.collectAsStateWithLifecycle(initialValue = emptyList())
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf<Uuid?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<McpProbe.Report?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val selected = servers.firstOrNull { it.id == id }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.mcp_probe_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.mcp_probe_scope), style = MaterialTheme.typography.bodySmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !busy && servers.isNotEmpty()) {
                Text(selected?.commonOptions?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.mcp_probe_pick))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                servers.forEach { server ->
                    DropdownMenuItem(text = { Text((server.commonOptions.name.takeIf { it.isNotBlank() }
                        ?: server.id.toString().take(8)) + " · " + McpDiagnostics.safeEndpoint(server.serverUrl)) },
                        onClick = { id = server.id; expanded = false; report = null; unavailable = false })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val target = selected?.id
            TextButton(enabled = target != null && !busy, onClick = {
                if (target != null && !busy) {
                    report = null
                    unavailable = false
                    busy = true
                    job = scope.launch {
                        try { report = manager.diagnose(target); unavailable = report == null }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { unavailable = true }
                        finally { busy = false }
                    }
                }
            }) { Text(stringResource(R.string.mcp_probe_run)) }
            if (busy) TextButton(onClick = { job?.cancel() }) { Text(stringResource(R.string.mcp_probe_cancel)) }
        }
        if (unavailable) Text(stringResource(R.string.mcp_probe_unavailable))
        report?.let { result ->
            val advice = stringResource(when (result.hint) {
                Hint.OK -> R.string.mcp_probe_ok
                Hint.NO_TOOLS_CAPABILITY -> R.string.mcp_probe_no_tools_capability
                Hint.CONFIG -> R.string.mcp_probe_config
                Hint.SSE_MANUAL -> R.string.mcp_probe_sse
                Hint.CLEARTEXT -> R.string.mcp_probe_cleartext
                Hint.AUTH -> R.string.mcp_probe_auth
                Hint.PATH -> R.string.mcp_probe_path
                Hint.REDIRECT -> R.string.mcp_probe_redirect
                Hint.SERVER_HTTP -> R.string.mcp_probe_http
                Hint.DNS -> R.string.mcp_probe_dns
                Hint.TLS -> R.string.mcp_probe_tls
                Hint.TCP_REFUSED -> R.string.mcp_probe_refused
                Hint.TCP_TIMEOUT -> R.string.mcp_probe_timeout
                Hint.TCP_OTHER -> R.string.mcp_probe_connect
                Hint.DEADLINE -> R.string.mcp_probe_deadline
                Hint.MCP_INIT -> R.string.mcp_probe_initialize
                Hint.TOOLS_LIST -> R.string.mcp_probe_tools
            })
            Text(advice, style = MaterialTheme.typography.bodyMedium)
            SelectionContainer { Text(result.summary(), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("MCP probe", result.summary() + "\n" + advice)))
            } }) { Text(stringResource(R.string.mcp_diagnostics_copy)) }
        }
    }
}
