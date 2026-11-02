package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import me.rerere.rikkahub.data.export.PaletteSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表面族的口气：改「表层色」要带动整族 surfaceContainer*，
 * 改「背景色」不能悄悄把容器也改了。色号解析的用例在 ColorHexTest。
 */
class PaletteTest {

    /**
     * 比色统一换成 ARGB。
     *
     * 注意不能用 `Color.value`：它把分量打包在高位，低 32 位只留色彩空间 id，
     * 截断之后所有 sRGB 颜色都会变成同一个数。
     */
    private val Color.bits: Long get() = toArgb().toLong() and 0xFFFFFFFFL

    @Test
    fun `surface color drives the whole surface family`() {
        val plain = PaletteColors().generateColorScheme(dark = false)
        val green = PaletteColors(surface = 0xFF1B5E20).generateColorScheme(dark = false)

        assertNotEquals(plain.surface.bits, green.surface.bits)
        assertNotEquals(plain.surfaceContainer.bits, green.surfaceContainer.bits)
        assertNotEquals(plain.surfaceContainerHigh.bits, green.surfaceContainerHigh.bits)
        assertNotEquals(plain.surfaceBright.bits, green.surfaceBright.bits)
    }

    @Test
    fun `background no longer moves the surface containers`() {
        val plain = PaletteColors().generateColorScheme(dark = false)
        val red = PaletteColors(background = 0xFFFF0000).generateColorScheme(dark = false)

        assertEquals(plain.surfaceContainer.bits, red.surfaceContainer.bits)
        assertEquals(plain.surfaceContainerHighest.bits, red.surfaceContainerHighest.bits)
        // 背景色自己还是被采纳的
        assertNotEquals(plain.background.bits, red.background.bits)
    }

    @Test
    fun `light and dark keep their own surface tones`() {
        val colors = PaletteColors(surface = 0xFF116682)
        val light = colors.generateColorScheme(dark = false)
        val dark = colors.generateColorScheme(dark = true)

        // 浅色容器贴白，深色容器贴黑，两者不可能相等
        assertNotEquals(light.surfaceContainer.bits, dark.surfaceContainer.bits)
    }

    @Test
    fun `seed expands into a full palette`() {
        val colors = PaletteColors.fromSeed(0xFF116682, dark = false)

        assertEquals(0xFF116682L, colors.primary)
        // 换种子不影响错误色：红要留在红，否则出错提示和正文分不开
        assertEquals(colors.error, PaletteColors.fromSeed(0xFF8B5000, dark = false).error)
        // 表面族由 surface 展开，不该还是九个默认值里的白
        assertTrue(colors.surface != 0xFFFEF7FFL)
    }

    @Test
    fun `surface lands verbatim`() {
        val green = PaletteColors(surface = 0xFF1B5E20).generateColorScheme(dark = false)

        // 重算表面族时不能把用户选的 surface 本身也改掉
        assertEquals(Color(0xFF1B5E20).bits, green.surface.bits)
    }

    @Test
    fun `seed drops alpha`() {
        // 带透明度的 primary 会让按钮和顶栏透出下层，色卡一律不透明
        assertEquals(0xFF112233L, PaletteColors.fromSeed(0x80112233, dark = false).primary)
    }

    @Test
    fun `non palette json is rejected`() {
        // PaletteSetting 字段全有默认值，没有这道闸门的话任意 JSON 都会被"成功"导入成空白
        assertTrue(PaletteSerializer.importFromJson("{}").isFailure)
        assertTrue(PaletteSerializer.importFromJson("""{"name":"x"}""").isFailure)
        // 信封对得上、data 是空的，同样不该被当成一份有效配置
        assertTrue(
            PaletteSerializer.importFromJson("""{"version":1,"type":"palette","data":{}}""").isFailure
        )
    }

    @Test
    fun `palette json round trips through the export envelope`() {
        val original = PaletteSetting(
            presetId = "ocean",
            light = PaletteColors(primary = 0xFF112233, surface = 0xFFF0F0F0),
            dark = PaletteColors(primary = 0xFF778899, surface = 0x80112233),
        )

        val json = PaletteSerializer.exportToJson(original)
        val restored = PaletteSerializer.importFromJson(json).getOrThrow()

        assertEquals(original, restored)
    }

    @Test
    fun `palette json also accepts a bare object`() {
        val bare = """{"presetId":"forest","light":null,"dark":null}"""

        val restored = PaletteSerializer.importFromJson(bare).getOrThrow()

        assertEquals("forest", restored.presetId)
        assertTrue(restored.enabled)
    }
}
