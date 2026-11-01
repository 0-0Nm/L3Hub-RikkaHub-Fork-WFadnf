package me.rerere.rikkahub.ui.pages.setting

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.remote.toProxyOrNull
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.shadow.FilledTonalButton
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.components.ui.NumberSlider
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.plus
import me.rerere.ui.toast.ToastType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

private const val PROXY_TEST_URL = "https://www.google.com/generate_204"

@Composable
fun SettingPreferencesNetworkPage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val httpClient = koinInject<OkHttpClient>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var userAgent by remember(settings.networkSetting.userAgent) {
        mutableStateOf(settings.networkSetting.userAgent)
    }
    var proxyUrl by remember(settings.networkSetting.proxyUrl) {
        mutableStateOf(settings.networkSetting.proxyUrl)
    }
    var proxyUsername by remember(settings.networkSetting.proxyUsername) {
        mutableStateOf(settings.networkSetting.proxyUsername)
    }
    var proxyPassword by remember(settings.networkSetting.proxyPassword) {
        mutableStateOf(settings.networkSetting.proxyPassword)
    }
    var proxyUrlDraft by remember { mutableStateOf("") }
    var proxyUsernameDraft by remember { mutableStateOf("") }
    var proxyPasswordDraft by remember { mutableStateOf("") }
    var proxyPasswordVisible by remember { mutableStateOf(false) }
    var proxyDialogVisible by remember { mutableStateOf(false) }
    val defaultUserAgent = "RikkaHub-Android/${BuildConfig.VERSION_NAME}"
    val proxyUrlInvalid = proxyUrlDraft.isNotBlank() && proxyUrlDraft.toProxyOrNull() == null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val caImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val pem = bytes?.let { certificateToPem(it) }
            if (pem == null) {
                toaster.show("无法读取证书文件", type = ToastType.Error)
            } else {
                vm.updateSettings { latest ->
                    if (pem in latest.customCaCertificates) {
                        latest
                    } else {
                        latest.copy(customCaCertificates = latest.customCaCertificates + pem)
                    }
                }
            }
        }
    }
    var proxyTesting by remember { mutableStateOf(false) }

    fun updateNetworkSetting(transform: (NetworkSetting) -> NetworkSetting) {
        vm.updateSettings {
            it.copy(networkSetting = transform(it.networkSetting))
        }
    }

    fun updateUserAgent(value: String) {
        userAgent = value
        vm.updateSettings {
            it.copy(
                networkSetting = it.networkSetting.copy(userAgent = value),
            )
        }
    }

    fun saveProxy() {
        vm.updateSettings {
            it.copy(
                networkSetting = it.networkSetting.copy(
                    proxyUrl = proxyUrlDraft,
                    proxyUsername = proxyUsernameDraft,
                    proxyPassword = proxyPasswordDraft,
                ),
            )
        }
    }

    fun resetProxy() {
        proxyUrlDraft = ""
        proxyUsernameDraft = ""
        proxyPasswordDraft = ""
    }

    fun testProxy() {
        val proxy = proxyUrl.toProxyOrNull() ?: return
        if (proxyTesting) return
        proxyTesting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val testClient = httpClient.newBuilder()
                        .proxy(proxy)
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .callTimeout(15, TimeUnit.SECONDS)
                        .build()
                    testClient.newCall(
                        Request.Builder()
                            .url(PROXY_TEST_URL)
                            .head()
                            .build()
                    ).execute().use { response ->
                        if (response.code != 204) {
                            throw IOException("HTTP ${response.code} ${response.message}")
                        }
                    }
                }
            }
            result.onSuccess {
                toaster.show(
                    resources.getString(R.string.backup_page_connection_success),
                    type = ToastType.Success,
                )
            }.onFailure { error ->
                toaster.show(
                    resources.getString(
                        R.string.backup_page_connection_failed,
                        error.message.orEmpty(),
                    ),
                    type = ToastType.Error,
                )
            }
            proxyTesting = false
        }
    }

    if (proxyDialogVisible) {
        AlertDialog(
            onDismissRequest = { proxyDialogVisible = false },
            modifier = Modifier.imePadding(),
            title = {
                Text(stringResource(R.string.setting_page_preferences_network_proxy))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = proxyUrlDraft,
                        onValueChange = { proxyUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.setting_page_preferences_network_proxy))
                        },
                        placeholder = { Text("http://127.0.0.1:7890") },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (proxyUrlInvalid) {
                                        R.string.setting_page_preferences_network_proxy_invalid
                                    } else {
                                        R.string.setting_page_preferences_network_proxy_desc
                                    }
                                )
                            )
                        },
                        isError = proxyUrlInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = proxyUsernameDraft,
                        onValueChange = { proxyUsernameDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.backup_page_username)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = proxyPasswordDraft,
                        onValueChange = { proxyPasswordDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.backup_page_password)) },
                        visualTransformation = if (proxyPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { proxyPasswordVisible = !proxyPasswordVisible },
                            ) {
                                Icon(
                                    imageVector = if (proxyPasswordVisible) {
                                        HugeIcons.ViewOff
                                    } else {
                                        HugeIcons.View
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    TextButton(
                        onClick = ::resetProxy,
                        modifier = Modifier.align(Alignment.End),
                        enabled = proxyUrlDraft.isNotEmpty() ||
                            proxyUsernameDraft.isNotEmpty() ||
                            proxyPasswordDraft.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveProxy()
                        proxyDialogVisible = false
                    },
                    enabled = !proxyUrlInvalid,
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding(16.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup {
                switchItem(
                    checked = settings.networkSetting.enableAutoRetry,
                    onCheckedChange = { enabled ->
                        updateNetworkSetting { it.copy(enableAutoRetry = enabled) }
                    },
                    supportingContent = {
                        Text(stringResource(R.string.setting_page_preferences_network_auto_retry_desc))
                    },
                    headlineContent = {
                        Text(stringResource(R.string.setting_page_preferences_network_auto_retry))
                    },
                )
                if (settings.networkSetting.enableAutoRetry) {
                    formItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_preferences_network_retry_count))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_preferences_network_retry_count_desc))
                        },
                    ) {
                        NumberSlider(
                            value = settings.networkSetting.retryMaxCount,
                            onValueChange = { count ->
                                updateNetworkSetting { it.copy(retryMaxCount = count) }
                            },
                            valueRange = 0..64,
                        )
                    }
                    formItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_preferences_network_retry_interval))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_preferences_network_retry_interval_desc))
                        },
                    ) {
                        NumberSlider(
                            value = settings.networkSetting.retryIntervalSeconds,
                            onValueChange = { seconds ->
                                updateNetworkSetting { it.copy(retryIntervalSeconds = seconds) }
                            },
                            valueRange = 1..60,
                            suffix = "s",
                        )
                    }
                    formItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_preferences_network_retry_codes))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_preferences_network_retry_codes_desc))
                        },
                    ) {
                        RetryErrorCodePicker(
                            selected = settings.networkSetting.retryErrorCodes,
                            onValueChange = { codes ->
                                updateNetworkSetting { it.copy(retryErrorCodes = codes) }
                            },
                        )
                    }
                }
            }

            CardGroup(
                title = {
                    Text(stringResource(R.string.setting_page_preferences_network_user_agent))
                },
            ) {
                formItem {
                    OutlinedTextField(
                        value = userAgent,
                        onValueChange = ::updateUserAgent,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(defaultUserAgent) },
                        supportingText = {
                            Text(
                                stringResource(
                                    R.string.setting_page_preferences_network_user_agent_desc,
                                    defaultUserAgent,
                                )
                            )
                        },
                        singleLine = true,
                    )
                    TextButton(
                        onClick = { updateUserAgent("") },
                        modifier = Modifier.align(Alignment.End),
                        enabled = userAgent.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }

            CardGroup(
                title = {
                    Text(stringResource(R.string.setting_page_preferences_network_proxy))
                },
            ) {
                item(
                    onClick = {
                        proxyUrlDraft = proxyUrl
                        proxyUsernameDraft = proxyUsername
                        proxyPasswordDraft = proxyPassword
                        proxyPasswordVisible = false
                        proxyDialogVisible = true
                    },
                    headlineContent = {
                        Text(stringResource(R.string.setting_page_config))
                    },
                    supportingContent = {
                        Text(
                            if (proxyUrl.isBlank()) {
                                stringResource(
                                    R.string.setting_page_preferences_network_proxy_desc
                                )
                            } else {
                                proxyUrl
                            }
                        )
                    },
                    trailingContent = {
                        Icon(HugeIcons.ArrowRight01, contentDescription = null)
                    },
                )
                item(
                    headlineContent = {
                        Text(stringResource(R.string.setting_provider_page_test_connection))
                    },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = ::testProxy,
                            enabled = proxyUrl.toProxyOrNull() != null && !proxyTesting,
                        ) {
                            if (proxyTesting) {
                                LoadingIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                Text(stringResource(R.string.setting_provider_page_test))
                            }
                        }
                    },
                )
            }

            CardGroup(title = { Text(stringResource(R.string.setting_network_ca_title)) }) {
                settings.customCaCertificates.forEachIndexed { index, pem ->
                    item(
                        headlineContent = { Text(certificateLabel(pem, index)) },
                        supportingContent = { Text(stringResource(R.string.setting_network_ca_installed)) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    vm.updateSettings { latest ->
                                        latest.copy(
                                            customCaCertificates = latest.customCaCertificates - pem
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Delete01,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
                item(
                    onClick = { caImporter.launch("*/*") },
                    headlineContent = { Text(stringResource(R.string.setting_network_ca_import)) },
                    supportingContent = { Text(stringResource(R.string.setting_network_ca_import_desc)) },
                )
            }

        }
    }
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.setting_page_preferences_network)) },
                    navigationIcon = { BackButton() },
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors,
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = CustomColors.topBarColors.containerColor,
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                content()
            }
        }
    }
}

private fun certificateToPem(bytes: ByteArray): String? {
    val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
    if (text != null && "BEGIN CERTIFICATE" in text) return text
    return runCatching {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes))
        buildString {
            appendLine("-----BEGIN CERTIFICATE-----")
            appendLine(Base64.encodeToString(certificate.encoded, Base64.NO_WRAP))
            append("-----END CERTIFICATE-----")
        }
    }.getOrNull()
}

private fun certificateLabel(pem: String, index: Int): String = runCatching {
    CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate
}.getOrNull()
    ?.subjectX500Principal
    ?.name
    ?.let { name ->
        name.split(",")
            .firstOrNull { it.trim().startsWith("CN=") }
            ?.substringAfter("CN=")
    }
    ?: "CA ${index + 1}"

@Composable
private fun RetryValueDialog(
    title: String,
    initialValue: String,
    keyboardType: KeyboardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/** 常见的可重试状态码，点选后按逗号拼起来存 */
private val RETRY_ERROR_CODE_PRESETS = listOf(
    408,
    409,
    425,
    429,
    500,
    502,
    503,
    504,
    520,
    522,
    524,
    529,
)

@Composable
private fun RetryErrorCodePicker(
    selected: String,
    onValueChange: (String) -> Unit,
) {
    val codes = remember(selected) {
        selected.split(',', '，', ';', '；', ' ', '\n')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RETRY_ERROR_CODE_PRESETS.forEach { code ->
            FilterChip(
                selected = code in codes,
                onClick = {
                    val next = if (code in codes) codes - code else codes + code
                    onValueChange(next.sorted().joinToString(","))
                },
                label = {
                    Text(
                        text = code.toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}
