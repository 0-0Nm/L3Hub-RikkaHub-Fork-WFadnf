package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.agent.withRetry
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.cache.LruCache
import me.rerere.common.cache.SingleFileCacheStore
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.UsageRecorder
import me.rerere.rikkahub.data.ai.UsageSource
import me.rerere.rikkahub.data.ai.UsageTag
import me.rerere.rikkahub.data.ai.retryPolicy
import me.rerere.rikkahub.data.datastore.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.days

private const val TAG = "OcrTransformer"

/** 同时识别几张图：再多容易被上游按并发限流 */
private const val MAX_CONCURRENT_OCR = 3

/** 没有能看图的模型时给模型的话，比 "[Image]" 更说明问题 */
private const val NO_OCR_MODEL_NOTICE =
    "No vision-capable OCR model is configured, so this image could not be read. " +
        "Tell the user the image is not visible to you, " +
        "and that they can pick one in Settings > Models > OCR model."

private const val EMPTY_RESPONSE_NOTICE =
    "OCR returned no text; the image may be blank or its text is not legible."

/**
 * 主模型看不了图时，把本地图片送去 OCR 模型转成文字。
 *
 * 只处理 file: 开头的图片。替换只发生在这一次请求的消息副本上，会话里的图片原样保留。
 */
object OcrTransformer : InputMessageTransformer, KoinComponent {
    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        val images = messages.ocrImages()
        if (images.isEmpty()) return messages

        return withContext(Dispatchers.IO) {
            try {
                val texts = recognizeAll(ctx, ctx.settings, images)
                messages.replaceOcrImages(texts)
            } finally {
                ctx.processingStatus.value = null
            }
        }
    }

    /**
     * 逐张识别，返回与 [images] 等长、已经包好标签的文本。
     *
     * 并发只影响速度：每张图还是独立请求，某一张失败不会牵连其它张。命中缓存的不占并发额度，
     * 也不再去改进度文案。
     */
    private suspend fun recognizeAll(
        ctx: TransformerContext,
        settings: Settings,
        images: List<UIMessagePart.Image>,
    ): List<String> {
        val target = settings.resolveOcrTarget()
        if (target == null) {
            Log.w(TAG, "recognizeAll: no vision-capable model configured for OCR")
            return images.indices.map { index ->
                wrapOcrResult(NO_OCR_MODEL_NOTICE, index + 1, images.size)
            }
        }

        val keys = images.map { ocrCacheKey(target.model.id, settings.ocrPrompt, it.url) }
        // 同一张图可能在一段历史里出现多次：按缓存键去重，只识别一次
        val uniqueKeys = keys.distinct()
        val positionByKey = uniqueKeys.withIndex().associate { (position, key) -> key to position }
        // 缓存里存的是没包标签的原始文本，第几张要等真正插入时再算，否则换位置后标签会撒谎
        val rawTexts = arrayOfNulls<String>(uniqueKeys.size)
        val pending = mutableListOf<Int>()
        uniqueKeys.forEachIndexed { position, key ->
            val cached = cache.get(key)
            if (cached != null) {
                rawTexts[position] = cached
            } else {
                pending.add(position)
            }
        }

        if (pending.isNotEmpty()) {
            val finished = AtomicInteger(0)
            val semaphore = Semaphore(MAX_CONCURRENT_OCR)
            coroutineScope {
                pending.map { position ->
                    async {
                        semaphore.withPermit {
                            val key = uniqueKeys[position]
                            ctx.processingStatus.value = ctx.context.getString(
                                R.string.chat_page_ocr_processing,
                                finished.incrementAndGet(),
                                pending.size,
                            )
                            val outcome = performOcr(settings, target, images[keys.indexOf(key)])
                            if (outcome.cacheable) {
                                cache.put(key, outcome.content)
                            }
                            rawTexts[position] = outcome.content
                        }
                    }
                }.awaitAll()
            }
        }

        return keys.mapIndexed { index, key ->
            val raw = rawTexts[positionByKey.getValue(key)] ?: EMPTY_RESPONSE_NOTICE
            wrapOcrResult(raw, index + 1, images.size)
        }
    }

    private suspend fun performOcr(
        settings: Settings,
        target: OcrTarget,
        part: UIMessagePart.Image,
    ): OcrOutcome = try {
        val provider = get<UsageRecorder>().wrap(
            get<ProviderManager>().getProviderByType(target.providerSetting),
            UsageTag(UsageSource.OCR),
        )
        val result = withRetry(
            policy = settings.retryPolicy(),
            onRetry = { Log.w(TAG, "performOcr: retry ${it.retry}/${it.maxRetries} in ${it.delay}", it.cause) },
        ) {
            provider.generateText(
                providerSetting = target.providerSetting,
                messages = listOf(
                    UIMessage.system(settings.ocrPrompt),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(part.url))
                    )
                ),
                params = TextGenerationParams(
                    model = target.model,
                    customHeaders = target.model.customHeaders,
                    customBody = target.model.customBodies,
                ),
            )
        }
        val content = result.message.toText().trim()
        if (content.isBlank()) {
            Log.w(TAG, "performOcr: empty response for ${part.url}")
            OcrOutcome(EMPTY_RESPONSE_NOTICE, cacheable = false)
        } else {
            Log.i(TAG, "performOcr: $content")
            OcrOutcome(content, cacheable = true)
        }
    } catch (e: CancellationException) {
        // 用户停止生成时得真的停下来，不能被下面的兜底吞掉
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "performOcr: failed for ${part.url}", e)
        OcrOutcome(ocrFailureMessage(e), cacheable = false)
    }

    /** 识别结果；空回复和失败不进缓存，否则用户重发同一张图永远拿不到正确内容 */
    private data class OcrOutcome(
        val content: String,
        val cacheable: Boolean,
    )
}
