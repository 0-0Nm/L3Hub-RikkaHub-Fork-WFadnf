package me.rerere.rikkahub.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 色号解析与格式化。取色器的输入框和「从色号生成」都走这两个函数，
 * 所以粘贴、简写、非法输入这几种情况都要能对得上。
 */
class ColorHexTest {

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
    fun `complete hex detection ignores shorthand`() {
        assertTrue(isCompleteHexColor("#466E7A"))
        assertTrue(isCompleteHexColor("#80466E7A"))
        assertFalse(isCompleteHexColor("#ABC"))
        assertFalse(isCompleteHexColor("#466"))
    }

    @Test
    fun `alpha helpers keep rgb intact`() {
        assertEquals(0x80L, alphaComponent(0x80466E7A).toLong())
        assertEquals(0x00466E7AL, withAlpha(0xFF466E7A, 0))
        assertEquals(0x80466E7AL, withAlpha(0xFF466E7A, 128))
    }
}
