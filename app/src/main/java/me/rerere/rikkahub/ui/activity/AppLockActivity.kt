package me.rerere.rikkahub.ui.activity

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.AppLockStateStore
import me.rerere.rikkahub.ui.components.ui.shadow.Button
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.AppLanguage
import me.rerere.rikkahub.utils.hasDeviceCredential

/**
 * 锁屏页单独占一个 Activity：它盖在 RouteActivity 的整个窗口之上，
 * 里面已经打开的 Dialog、ModalBottomSheet 等子窗口不会露出来，也无法继续交互。
 */
class AppLockActivity : ComponentActivity() {
    private val appLockState by lazy { AppLockStateStore(this) }
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    private var canVerify by mutableStateOf(true)
    private var allowBypass by mutableStateOf(false)
    private var prompting by mutableStateOf(false)
    private var autoPrompted = false
    private var unlocked = false
    private var paused = false

    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        prompting = false
        if (result.resultCode == Activity.RESULT_OK) {
            finishUnlocked()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        canVerify = hasDeviceCredential()
        allowBypass = !canVerify

        setContent {
            RikkahubTheme {
                AppLockScreen(
                    onUnlock = ::requestUnlock,
                    canVerify = canVerify,
                    allowBypass = allowBypass,
                    onEnterAnyway = ::finishUnlocked,
                    onExit = { finishAffinity() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (unlocked || prompting) return
        if (!appLockState.shouldLock()) {
            unlocked = true
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        appLockState.markLocked()
        canVerify = hasDeviceCredential()
        allowBypass = !canVerify
        if (autoPrompted || !canVerify) return
        autoPrompted = true
        requestUnlock()
    }

    override fun onResume() {
        super.onResume()
        if (!unlocked && paused) {
            paused = false
            prompting = false
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    private fun requestUnlock() {
        if (unlocked || prompting) return
        val intent = runCatching {
            keyguard?.createConfirmDeviceCredentialIntent(
                getString(R.string.app_lock_confirm_title),
                getString(R.string.app_lock_confirm_desc),
            )
        }.getOrNull()
        if (intent == null) {
            canVerify = false
            allowBypass = !hasDeviceCredential()
            return
        }
        canVerify = true
        prompting = true
        runCatching { unlockLauncher.launch(intent) }
            .onFailure {
                prompting = false
                canVerify = false
                allowBypass = !hasDeviceCredential()
            }
    }

    private fun finishUnlocked() {
        if (unlocked) return
        unlocked = true
        appLockState.clear()
        setResult(Activity.RESULT_OK)
        finish()
    }
}

@Composable
internal fun AppLockScreen(
    onUnlock: () -> Unit,
    canVerify: Boolean = true,
    allowBypass: Boolean = false,
    onEnterAnyway: () -> Unit = onUnlock,
    onExit: (() -> Unit)? = null,
) {
    BackHandler(enabled = true) {}
    val enterAnyway = !canVerify && allowBypass
    val verifyFailed = !canVerify && !allowBypass
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_lock_locked_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = when {
                    enterAnyway -> stringResource(R.string.app_lock_verify_unavailable)
                    verifyFailed -> stringResource(R.string.app_lock_verify_failed)
                    else -> stringResource(R.string.app_lock_locked_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (canVerify) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = if (enterAnyway) onEnterAnyway else onUnlock,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text(
                    stringResource(
                        if (enterAnyway) R.string.app_lock_enter_anyway else R.string.app_lock_unlock
                    )
                )
            }
            if (verifyFailed && onExit != null) {
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.app_lock_exit))
                }
            }
        }
    }
}
