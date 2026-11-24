package me.rerere.rikkahub.data.ai.mcp

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.util.Log
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

/** Observations of this UID only. No routes, network identifiers, raw errors or credentials exported. */
internal object McpProbeEvidence {
    private const val TAG = "McpProbeReport"
    private val sequence = AtomicLong()

    fun addressFamily(address: InetAddress?): String = when (address) {
        is Inet4Address -> "IPV4"
        is Inet6Address -> "IPV6"
        else -> "UNKNOWN"
    }

    private fun state(value: Boolean?): String = when (value) {
        true -> "yes"
        false -> "no"
        null -> "unknown"
    }

    fun environment(context: Context): List<String> {
        val ownUser = try { Process.myUserHandle().toString().filter { it.isDigit() }.take(10)
            .ifEmpty { "unknown" } } catch (_: Exception) { "unknown" }
        val info = ActivityManager.RunningAppProcessInfo()
        val importance = try {
            ActivityManager.getMyMemoryState(info)
            when (info.importance) {
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground_service"
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
                else -> "other"
            }
        } catch (_: Exception) { "unknown" }
        val localNetwork = try {
            if (context.packageManager.getPermissionInfo("android.permission.ACCESS_LOCAL_NETWORK", 0).name.isEmpty()) "unknown"
            else if (context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED) "granted"
            else "not_granted"
        } catch (_: PackageManager.NameNotFoundException) { "not_exposed" }
          catch (_: Exception) { "unknown" }
        val identity = "INSTANCE user=$ownUser importance=$importance localNetworkPermission=$localNetwork"
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return listOf(identity, "ACTIVE_NETWORK unknown")
        val network = try {
            val active = cm.activeNetwork
            if (active == null) "ACTIVE_NETWORK not_available_to_instance" else {
                val caps = cm.getNetworkCapabilities(active)
                val transports = listOf(
                    NetworkCapabilities.TRANSPORT_WIFI to "WIFI",
                    NetworkCapabilities.TRANSPORT_CELLULAR to "CELLULAR",
                    NetworkCapabilities.TRANSPORT_ETHERNET to "ETHERNET",
                    NetworkCapabilities.TRANSPORT_VPN to "VPN",
                ).filter { caps?.hasTransport(it.first) == true }.joinToString("+") { it.second }.ifEmpty { "unknown_or_other" }
                "ACTIVE_NETWORK transports=$transports vpn=${state(caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN))}" +
                    " validated=${state(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))}" +
                    " metered=${state(cm.isActiveNetworkMetered)}"
            }
        } catch (_: Exception) { "ACTIVE_NETWORK unknown" }
        val background = try {
            when (cm.restrictBackgroundStatus) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "allowlisted"
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                else -> "unknown"
            }
        } catch (_: Exception) { "unknown" }
        val proxy = try {
            val config = cm.defaultProxy
            when {
                config == null -> "none_observed"
                config.pacFileUrl?.let { it != android.net.Uri.EMPTY } == true -> "pac"
                else -> "configured"
            }
        } catch (_: Exception) { "unknown" }
        return listOf(identity, network, "APP_NETWORK restrictBackground=$background defaultProxy=$proxy")
    }

    fun publish(report: McpProbe.Report) {
        // Reuse the already-redacted report. Do not log config, SDK messages or exception text.
        val id = sequence.incrementAndGet()
        val lines = report.summary().lineSequence().take(65).toList()
        lines.forEachIndexed { i, line -> Log.i(TAG, "report=$id part=${i + 1}/${lines.size} $line") }
    }
}
