package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * top_k 只发给接受它的 OpenAI 兼容服务：OpenAI 官方（含 Azure）不认识该字段，会直接 400。
 */
class ChatCompletionsTopKRequestTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    // Helper to invoke private buildChatCompletionRequest via reflection
    private fun buildRequest(baseUrl: String, topK: Int?): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        val params = TextGenerationParams(
            model = Model(modelId = "test-model"),
            topK = topK,
        )
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            ProviderSetting.OpenAI(baseUrl = baseUrl),
            true
        ) as JsonObject
    }

    @Test
    fun `top_k is sent to third party compatible endpoints`() {
        val body = buildRequest("https://api.deepseek.com/v1", 40)

        assertEquals("40", body["top_k"]!!.jsonPrimitive.content)
    }

    @Test
    fun `top_k is dropped for official openai endpoint`() {
        val body = buildRequest("https://api.openai.com/v1", 40)

        assertFalse("top_k" in body)
    }

    @Test
    fun `top_k is dropped for azure openai endpoint`() {
        val body = buildRequest("https://example.openai.azure.com/openai/v1", 40)

        assertFalse("top_k" in body)
    }

    @Test
    fun `top_k is dropped for xai endpoint`() {
        val body = buildRequest("https://api.x.ai/v1", 40)

        assertFalse("top_k" in body)
    }

    @Test
    fun `top_k is absent when unset`() {
        val body = buildRequest("https://api.deepseek.com/v1", null)

        assertFalse("top_k" in body)
    }
}
