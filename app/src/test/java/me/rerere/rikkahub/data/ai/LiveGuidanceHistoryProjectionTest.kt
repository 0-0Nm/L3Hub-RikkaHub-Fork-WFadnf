package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.ArchivedLiveGuidance
import me.rerere.rikkahub.data.model.ConversationConfig
import me.rerere.rikkahub.data.model.LiveGuidance
import me.rerere.rikkahub.data.model.LiveGuidanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * Provider-visible role/order contract for archived guidance history projection and fresh
 * live steering. These tests only build the final request message list; no model is involved.
 */
class LiveGuidanceHistoryProjectionTest {
    private fun archive(
        content: String,
        mode: LiveGuidanceMode,
        anchor: UIMessage,
    ) = ArchivedLiveGuidance(
        id = Uuid.random(),
        runId = Uuid.random(),
        content = content,
        mode = mode,
        nodeId = Uuid.random(),
        messageId = anchor.id,
    )

    @Test
    fun `earlier table preference and newer paragraph request keep chronological priority`() {
        val question = UIMessage.user("总结一下")
        val reply = UIMessage.assistant("答案")
        val table = archive("用表格", LiveGuidanceMode.PERSISTENT, reply)
        val newer = UIMessage.user("改成段落")

        val messages = listOf(question, reply, newer).withArchivedGuidance(listOf(table))

        // [user 总结一下, assistant 答案, user 用表格, user 改成段落]
        assertEquals(4, messages.size)
        assertEquals(listOf(question.id, reply.id, table.id, newer.id), messages.map { it.id })
        assertTrue(messages.all { it.role != MessageRole.SYSTEM })
        assertEquals(MessageRole.USER, messages[2].role)
        assertEquals("用表格", messages[2].toText())
        // The newest normal user instruction stays last, so it naturally prevails.
        assertEquals("改成段落", messages.last().toText())
        assertFalse(messages.last().isSynthetic)
    }

    @Test
    fun `persistent archive is recalled by later requests while its anchor is retained`() {
        val reply = UIMessage.assistant("old reply")
        val note = archive("偏好简短回答", LiveGuidanceMode.PERSISTENT, reply)
        val firstRequest = listOf(reply, UIMessage.user("问题一")).withArchivedGuidance(listOf(note))
        val laterRequest = listOf(reply, UIMessage.user("问题一"), UIMessage.assistant("回答一"), UIMessage.user("问题二"))
            .withArchivedGuidance(listOf(note))

        assertEquals(note.id, firstRequest[1].id)
        assertEquals(note.id, laterRequest[1].id)
        assertEquals(1, laterRequest.count { it.id == note.id })
        assertEquals("问题二", laterRequest.last().toText())
    }

    @Test
    fun `nothing archived is injected after the latest normal user message`() {
        val reply = UIMessage.assistant("reply")
        val persistent = archive("old persistent", LiveGuidanceMode.PERSISTENT, reply)
        val temporary = archive("old temporary", LiveGuidanceMode.TEMPORARY, reply)
        val latest = UIMessage.user("latest normal user")

        val messages = listOf(reply, latest).withArchivedGuidance(listOf(persistent, temporary))

        assertEquals(latest.id, messages.last().id)
        assertTrue(messages.dropWhile { it.id != reply.id }.drop(1).dropLast(1).all {
            it.role == MessageRole.USER && it.isSynthetic
        })
        assertEquals(listOf(persistent.id, temporary.id), messages.subList(1, 3).map { it.id })
    }

    @Test
    fun `message only chat without any tools projects plain text user history`() {
        val reply = UIMessage.assistant("plain answer")
        val note = archive("再短一点", LiveGuidanceMode.TEMPORARY, reply)

        val messages = listOf(UIMessage.user("hi"), reply, UIMessage.user("继续"))
            .withArchivedGuidance(listOf(note))

        val projected = messages.single { it.id == note.id }
        assertEquals(MessageRole.USER, projected.role)
        assertTrue(projected.parts.single() is UIMessagePart.Text)
        assertTrue(projected.getTools().isEmpty())
        assertEquals("再短一点", projected.toText())
    }

    @Test
    fun `archives are scoped to their own conversation config only`() {
        val reply = UIMessage.assistant("reply")
        val foreign = archive("other conversation", LiveGuidanceMode.PERSISTENT, reply)
        val own = ConversationConfig()
        val other = ConversationConfig(liveGuidanceArchive = listOf(foreign))

        assertTrue(own.liveGuidanceArchive.isEmpty())
        assertEquals(1, other.liveGuidanceArchive.size)
        // A request without that config's archive snapshot is byte-for-byte unaffected.
        val base = listOf(reply, UIMessage.user("question"))
        assertEquals(base, base.withArchivedGuidance(own.liveGuidanceArchive))
        val projected = base.withArchivedGuidance(other.liveGuidanceArchive)
        assertEquals(1, projected.count { it.id == foreign.id })
        assertEquals(MessageRole.USER, projected.single { it.id == foreign.id }.role)
    }

    @Test
    fun `missing or pruned anchor drops temporary and degrades persistent to bounded prefix`() {
        val pruned = UIMessage.assistant("no longer retained")
        val temporary = archive("temporary lost", LiveGuidanceMode.TEMPORARY, pruned)
        val persistent = archive("persistent lost", LiveGuidanceMode.PERSISTENT, pruned)
        val system = UIMessage.system("system prompt").copy(isSynthetic = true)
        val retained = UIMessage.user("retained question")

        val messages = listOf(system, retained, UIMessage.assistant("retained answer"))
            .withArchivedGuidance(listOf(temporary, persistent))

        assertTrue(messages.none { it.id == temporary.id })
        val fallback = messages.single { it.id == persistent.id }
        assertEquals(MessageRole.USER, fallback.role)
        assertTrue(fallback.toText().endsWith("persistent lost"))
        assertTrue(fallback.toText() != "persistent lost")
        // Fallback sits after the system prompt and before ALL retained history, never after
        // the latest normal user message.
        assertEquals(1, messages.indexOf(fallback))
        assertEquals(system.id, messages.first().id)
        assertEquals(retained.id, messages[2].id)
    }

    @Test
    fun `trailing anchor never fabricates an unanswered user turn at the end`() {
        val reply = UIMessage.assistant("reply being retried")
        val temporary = archive("temp", LiveGuidanceMode.TEMPORARY, reply)
        val persistent = archive("persist", LiveGuidanceMode.PERSISTENT, reply)

        val messages = listOf(UIMessage.user("q"), reply).withArchivedGuidance(listOf(temporary, persistent))

        // Retry/alternate generation still ends with the assistant reply to continue.
        assertEquals(reply.id, messages.last().id)
        assertTrue(messages.none { it.id == temporary.id })
        assertEquals(0, messages.indexOfFirst { it.id == persistent.id })
        assertEquals("q", messages[1].toText())
    }

    @Test
    fun `duplicate records and repeated anchors keep stable order and one copy`() {
        val reply = UIMessage.assistant("reply")
        val first = archive("first", LiveGuidanceMode.PERSISTENT, reply)
        val second = archive("second", LiveGuidanceMode.TEMPORARY, reply)

        val messages = listOf(reply, UIMessage.user("next"))
            .withArchivedGuidance(listOf(first, second, first.copy()))

        assertEquals(listOf(reply.id, first.id, second.id), messages.dropLast(1).map { it.id })
        assertEquals(1, messages.count { it.id == first.id })
    }

    @Test
    fun `tool calls and results stay inside their untouched assistant message`() {
        val tool = UIMessagePart.Tool(
            toolCallId = "call_1",
            toolName = "search",
            input = "{}",
            output = listOf(UIMessagePart.Text("result")),
        )
        val reply = UIMessage.assistant("").copy(parts = listOf(UIMessagePart.Text("working"), tool))
        val note = archive("focus", LiveGuidanceMode.TEMPORARY, reply)

        val messages = listOf(reply, UIMessage.user("next")).withArchivedGuidance(listOf(note))

        val untouched = messages.first { it.id == reply.id }
        assertEquals(reply.parts, untouched.parts)
        assertEquals(listOf(reply.id, note.id), messages.dropLast(1).map { it.id })
    }

    @Test
    fun `fresh live guidance is plain user text newer than the latest normal user message`() {
        val run = Uuid.random()
        val live = LiveGuidance(content = "现在改用列表", runId = run)
        val latest = UIMessage.user("latest")
        val base = listOf(UIMessage.user("old"), UIMessage.assistant("answer"), latest)

        val messages = base.withRequestInjections(
            ConversationConfig(liveGuidance = live).liveGuidanceRequestMessages(run),
        )

        val injected = messages.last()
        assertEquals(live.id, injected.id)
        assertEquals(MessageRole.USER, injected.role)
        assertEquals("现在改用列表", injected.toText())
        assertTrue(injected.isSynthetic)
        assertEquals(latest.id, messages[messages.size - 2].id)
        // No canned imperative frame is added ahead of the user's own wording.
        assertFalse(injected.toText().contains("Live steering"))
    }
}
