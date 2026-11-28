package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPickerProviderTitleTest {
    private val fallback = "选择模型"

    @Test fun configuredNameIsNotImplementationOrModelName() {
        val provider = ProviderSetting.OpenAI(
            name = "我的专属供应商 / Production East",
            models = listOf(Model(modelId = "gpt-test", displayName = "Not the provider name")),
        )
        assertEquals(provider.name, modelPickerProviderTitle(provider, false, fallback))
    }

    @Test fun changingHighlightedProviderChangesTitleRegardlessOfImplementation() {
        val first = ProviderSetting.OpenAI(name = "第一供应商")
        val second = ProviderSetting.OpenAI(name = "第二供应商")
        assertEquals(first.name, modelPickerProviderTitle(first, false, fallback))
        assertEquals(second.name, modelPickerProviderTitle(second, false, fallback))
    }

    @Test fun renameOnSameIdIsReadWithoutCachingTheOldName() {
        val provider = ProviderSetting.OpenAI(name = "原名称")
        assertEquals("原名称", modelPickerProviderTitle(provider, false, fallback))
        assertEquals("新名称", modelPickerProviderTitle(provider.copy(name = "新名称"), false, fallback))
    }

    @Test fun fullLongNameAndIntentionalWhitespaceArePreserved() {
        val name = "  自定义供应商 / " + "完整名称不可缩略 LongConfiguredName-".repeat(40) + "  "
        assertEquals(name, modelPickerProviderTitle(ProviderSetting.Google(name = name), false, fallback))
    }

    @Test fun favoritesNeverLeaksPreviouslyHighlightedProviderName() {
        assertEquals(fallback, modelPickerProviderTitle(ProviderSetting.OpenAI(name = "Hidden"), true, fallback))
    }

    @Test fun absentUnresolvedAndBlankProvidersUseOriginalLocalizedFallback() {
        assertEquals(fallback, modelPickerProviderTitle(null, false, fallback))
        assertEquals(fallback, modelPickerProviderTitle(ProviderSetting.OpenAI(name = ""), false, fallback))
        assertEquals(fallback, modelPickerProviderTitle(ProviderSetting.OpenAI(name = "  \t"), false, fallback))
        assertEquals("Select a model", modelPickerProviderTitle(null, true, "Select a model"))
    }
}
