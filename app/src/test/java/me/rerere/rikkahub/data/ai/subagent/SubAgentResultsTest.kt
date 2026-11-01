package me.rerere.rikkahub.data.ai.subagent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class SubAgentResultsTest {
    @Test
    fun `single result keeps the header and the answer`() {
        val text = formatSubAgentResults(listOf("子代理「X」已完成\n\n答案"))
        assertTrue(text.startsWith("1 parallel sub-agent task(s) have finished"))
        assertTrue(text.endsWith("## 1\n子代理「X」已完成\n\n答案"))
    }

    @Test
    fun `several results merge into one message in completion order`() {
        val text = formatSubAgentResults(listOf("第一份", "第二份"))
        assertTrue(text.contains("2 parallel sub-agent task(s) have finished"))
        assertTrue(text.indexOf("## 1") < text.indexOf("第一份"))
        assertTrue(text.indexOf("第一份") < text.indexOf("## 2"))
        assertTrue(text.indexOf("## 2") < text.indexOf("第二份"))
    }

    @Test
    fun `notice body never claims to be user input`() {
        val text = formatSubAgentResults(listOf("结果"))
        assertFalse(text.contains("用户输入"))
    }

    @Test
    fun `each result is trimmed but inner blank lines survive`() {
        val text = formatSubAgentResults(listOf("  第一段\n\n第二段  "))
        assertTrue(text.endsWith("## 1\n第一段\n\n第二段"))
    }

    @Test
    fun `each round of the same sub-agent is told apart`() {
        val id = Uuid.random()
        val first = formatSubAgentRoundResult("X", id, 1, "  第一轮的答案  ")
        val second = formatSubAgentRoundResult("X", id, 2, "第二轮的答案")
        assertTrue(first.contains("finished round 1"))
        assertTrue(second.contains("finished round 2"))
        assertTrue(first.endsWith("第一轮的答案"))
        assertTrue(second.endsWith("第二轮的答案"))
        assertFalse(first.contains("its own sub-agents sent back"))
        assertTrue(second.contains("its own sub-agents sent back"))
    }

    @Test
    fun `a nameless sub-agent falls back to its conversation id`() {
        val id = Uuid.random()
        val text = formatSubAgentRoundResult("", id, 1, "答案")
        assertTrue(text.contains(id.toString()))
    }
}
