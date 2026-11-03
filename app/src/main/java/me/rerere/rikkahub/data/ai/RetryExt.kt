package me.rerere.rikkahub.data.ai

import me.rerere.ai.agent.RetryPolicy
import me.rerere.rikkahub.data.datastore.Settings
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** 设置页里的自动重试配置；对话、压缩、OCR 这些请求共用一份 */
internal fun Settings.retryPolicy(): RetryPolicy {
    val network = networkSetting
    if (!network.enableAutoRetry) return RetryPolicy.NONE
    // 错误码以逗号分隔，允许中英文逗号、空格和分号
    val errorCodes = network.retryErrorCodes
        .split(',', '，', ';', '；', ' ', '\n')
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()
    return RetryPolicy(
        maxRetries = network.retryMaxCount.coerceIn(0, 64),
        initialDelay = network.retryIntervalSeconds.coerceIn(1, 60).seconds,
        exponentialBackoff = false,
        retryOn = { error ->
            error is IOException || errorCodes.any { error.hasStatusHint(it) }
        },
    )
}

/** 服务端错误多数把状态码写在异常文本里，按完整数字匹配，避免 500 命中 1500 */
private fun Throwable.hasStatusHint(code: Int): Boolean =
    generateSequence(this) { it.cause }.any { cause ->
        cause.message?.let { message ->
            Regex("(?<!\\d)$code(?!\\d)").containsMatchIn(message)
        } == true
    }
