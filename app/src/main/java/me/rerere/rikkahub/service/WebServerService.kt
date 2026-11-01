package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.WEB_SERVER_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ssh.SshServerManager
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.rikkahub.web.WebServerState
import org.koin.android.ext.android.inject

private const val TAG = "WebServerService"

/**
 * 前台服务宿主：Web 服务器和内置 SSH 服务端共用一套生命周期和同一条通知。
 * 两个服务各有自己的开关，这里只负责按设置启动/停止并保持前台通知。
 */
class WebServerService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.WEB_SERVER_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.WEB_SERVER_STOP"
        const val ACTION_STOP_WEB = "me.rerere.rikkahub.action.WEB_SERVER_STOP_WEB_ONLY"
        const val ACTION_START_SSH = "me.rerere.rikkahub.action.SSH_SERVER_START"
        const val ACTION_STOP_SSH = "me.rerere.rikkahub.action.SSH_SERVER_STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_LOCALHOST_ONLY = "localhost_only"
        const val EXTRA_WEB_ENABLED = "web_enabled"
        const val NOTIFICATION_ID = 2001
    }

    private val webServerManager: WebServerManager by inject()
    private val sshServerManager: SshServerManager by inject()
    private val settingsStore: SettingsStore by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                val localhostOnly = intent.getBooleanExtra(EXTRA_LOCALHOST_ONLY, false)
                val webEnabled = intent.getBooleanExtra(EXTRA_WEB_ENABLED, true)
                if (!startForegroundCompat()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startObservingState()
                if (webEnabled) {
                    webServerManager.start(port = port, localhostOnly = localhostOnly)
                }
                startSshIfEnabled()
            }

            ACTION_START_SSH -> {
                if (!startForegroundCompat()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startObservingState()
                serviceScope.launch {
                    settingsStore.update { it.copy(sshServerEnabled = true) }
                    startSsh()
                }
            }

            // 只停 Web 服务器，SSH 那一路不受影响
            ACTION_STOP_WEB -> {
                webServerManager.stop()
                serviceScope.launch {
                    settingsStore.update { it.copy(webServerEnabled = false) }
                }
            }

            ACTION_STOP -> {
                webServerManager.stop()
                sshServerManager.stop()
                serviceScope.launch {
                    settingsStore.update { it.copy(webServerEnabled = false, sshServerEnabled = false) }
                }
                // 不立即 stopSelf，等状态流检测到停止后再结束
            }

            ACTION_STOP_SSH -> {
                sshServerManager.stop()
                serviceScope.launch {
                    settingsStore.update { it.copy(sshServerEnabled = false) }
                }
            }

            null -> {
                // 兜底：intent 为 null 时根据设置决定是否启动
                if (!startForegroundCompat()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                serviceScope.launch {
                    val settings = settingsStore.awaitLoaded()
                    if (settings.webServerEnabled) {
                        startObservingState()
                        webServerManager.start(
                            port = settings.webServerPort,
                            localhostOnly = settings.webServerLocalhostOnly
                        )
                    }
                    if (settings.sshServerEnabled) {
                        startObservingState()
                        startSsh()
                    }
                    if (!settings.webServerEnabled && !settings.sshServerEnabled) {
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sshServerManager.stop()
        serviceScope.cancel()
    }

    /** 打开 SSH：配置从当前设置里取，令牌沿用 Web 服务器那一份 */
    private suspend fun startSsh() {
        val settings = settingsStore.awaitLoaded()
        sshServerManager.start(
            port = settings.sshServerPort,
            token = settings.webServerAccessPassword,
            allowLan = settings.sshServerAllowLan
        )
    }

    private fun startSshIfEnabled() {
        serviceScope.launch {
            val settings = settingsStore.awaitLoaded()
            if (!settings.sshServerEnabled) return@launch
            sshServerManager.start(
                port = settings.sshServerPort,
                // 令牌沿用 Web 服务器那一份，密码为空时 sshd 拒绝启动
                token = settings.webServerAccessPassword,
                allowLan = settings.sshServerAllowLan
            )
        }
    }

    private fun startForegroundCompat(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildStartingNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildStartingNotification())
            }
            true
        } catch (e: Exception) {
            // 部分 OEM ROM (如 realme UI/ColorOS) 会在系统侧拒绝 FGS 类型权限，
            // 即使 Manifest 已声明 FOREGROUND_SERVICE_SPECIAL_USE 也会抛 SecurityException
            Log.e(TAG, "Failed to start foreground service", e)
            webServerManager.reportError("Failed to start foreground service: ${e.message}")
            false
        }
    }

    private fun startObservingState() {
        if (stateObserverJob != null) return
        stateObserverJob = serviceScope.launch {
            var wasRunning = false
            // error 也一起合并: SSH 启动失败时 isRunning 不会变化, 只看它永远等不到通知
            combine(
                webServerManager.state,
                sshServerManager.isRunning,
                sshServerManager.error,
            ) { web, ssh, sshError -> Triple(web, ssh, sshError) }
                .collect { (web, sshRunning, sshError) ->
                    val sshPort = sshServerManager.port.value
                    when {
                        web.isRunning || sshRunning -> {
                            wasRunning = true
                            updateNotification(buildRunningNotification(web, sshRunning, sshPort))
                        }

                        // 启动失败（端口占用、令牌为空）时没有可等的状态变化，直接收掉服务
                        web.error != null || sshError != null -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }

                        wasRunning && !web.isLoading -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
        }
    }

    private fun updateNotification(notification: android.app.Notification) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildLaunchPendingIntent() = PendingIntent.getActivity(
        this,
        0,
        packageManager.getLaunchIntentForPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildStartingNotification() = NotificationCompat.Builder(this, WEB_SERVER_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_rikkahub)
        .setContentTitle(getString(R.string.notification_channel_web_server))
        .setContentText(getString(R.string.notification_web_server_starting))
        .setContentIntent(buildLaunchPendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun buildRunningNotification(
        web: WebServerState,
        sshRunning: Boolean,
        sshPort: Int
    ): android.app.Notification {
        val lines = buildList {
            if (web.isRunning) {
                val host = if (web.localhostOnly) "localhost" else (web.address ?: "localhost")
                add("http://$host:${web.port}")
            }
            if (sshRunning) {
                add("ssh -p $sshPort l3hub@127.0.0.1")
            }
        }
        val title = if (web.isRunning || !sshRunning) {
            getString(R.string.notification_web_server_running)
        } else {
            getString(R.string.notification_ssh_server_running)
        }

        val stopIntent = Intent(this, WebServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WEB_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(buildLaunchPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, getString(R.string.notification_web_server_stop), stopPendingIntent)
            .build()
    }
}
