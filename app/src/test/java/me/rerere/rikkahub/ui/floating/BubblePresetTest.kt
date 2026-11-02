package me.rerere.rikkahub.ui.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubblePresetTest {
    // 只碰名字表：BubblePresets 会在类初始化时构造 6 个 ImageVector，
    // 纯 JVM 单测里不保证建得出来，那部分交给真机

    @Test
    fun `preset names are unique because they are persisted`() {
        assertEquals(BubblePresetNames.size, BubblePresetNames.toSet().size)
    }

    @Test
    fun `preset names are not blank`() {
        // 空串存进设置后，和「没选过」分不开
        assertTrue(BubblePresetNames.all { it.isNotBlank() })
    }
}
