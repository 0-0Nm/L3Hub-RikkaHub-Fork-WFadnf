package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.data.export.PaletteSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调色板的两个口径：
 * 1. 色号输入要稳——粘贴进来的东西不能被改写；
 * 2. 改「表层色」要带动整族 surfaceContainer*，改「背景色」不能悄悄把容器也改了。
 */
class PaletteTest {

    /** 比色时统一换成 64 位的 argb，避免 ULong / Int 的重载歧义。 */
    private val Color.bits: Long get() = value.toLong() and 0xFFFFFFFFL

    @Test
    fun `hex parses six and eight digit forms`() {
        assertEquals(0xFF466E7AL, parseHexColor("#466E7A"))
        assertEquals(0xFF466E7AL, parseHexColor("466e7a"))
        assertEquals(0x80466E7AL, parseHexColor("#80466E7A"))
        // 没有 alpha 就按不透明算，不要变成半透明的黑
        assertEquals(0xFF000000L, parseHexColor("#000000"))
    }

    @Test
    fun `hex expands three digit shorthand`() {
        assertEquals(0xFFAABBCCL, parseHexColor("#ABC"))
        assertEquals(0xFFAABBCCL, parseHexColor("abc"))
    }

    @Test
    fun `hex rejects garbage`() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor("rgb(1 2 3)"))
    }

    @Test
    fun `hex formatting hides alpha only when opaque`() {
        assertEquals("#466E7A", formatHexColor(0xFF466E7A))
        assertEquals("#80466E7A", formatHexColor(0x80466E7A))
        // 强制八位时，不透明色也写全
        assertEquals("#FF466E7A", formatHexColor(0xFF466E7A, forceAlpha = true))
    }

    @Test
    fun `alpha helpers keep rgb intact`() {
        assertEquals(0x80L, alphaComponent(0x80466E7A).toLong())
        assertEquals(0x00466E7AL, withAlpha(0xFF466E7A, 0))
        assertEquals(0x80466E7AL, withAlpha(0xFF466E7A, 128))
    }

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
        assertEquals(PaletteColors.DEFAULT_ERROR, colors.error)
        // 表面族由 surface 展开，不该还是九个默认值里的白
        assertTrue(colors.surface != 0xFFFEF7FFL)
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
