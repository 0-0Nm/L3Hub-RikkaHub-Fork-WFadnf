package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ChatInputStateTest {
    private fun editing(parts: List<UIMessagePart>) = ChatInputState().apply {
        editingMessage = Uuid.random()
        setContents(parts)
    }

    @Test
    fun `reasoning and text message allows editing reasoning in place`() {
        val reasoning = UIMessagePart.Reasoning("old thought")
        val state = editing(listOf(reasoning, UIMessagePart.Text("answer")))

        assertNotNull(state.editingReasoning)
        assertEquals(emptyList<UIMessagePart>(), state.messageContent)

        state.editingReasoning!!.setTextAndPlaceCursorAtEnd("new thought")
        state.textContent.setTextAndPlaceCursorAtEnd("new answer")

        assertEquals(
            listOf(reasoning.copy(reasoning = "new thought"), UIMessagePart.Text("new answer")),
            state.getContents(),
        )
    }

    @Test
    fun `blank reasoning is dropped`() {
        val state = editing(listOf(UIMessagePart.Reasoning("old thought"), UIMessagePart.Text("answer")))

        state.editingReasoning!!.setTextAndPlaceCursorAtEnd("  ")

        assertEquals(listOf(UIMessagePart.Text("answer")), state.getContents())
    }

    @Test
    fun `signed reasoning is not editable and kept as is`() {
        val reasoning = UIMessagePart.Reasoning(
            reasoning = "thought",
            metadata = buildJsonObject { put("signature", JsonPrimitive("sig")) },
        )
        val state = editing(listOf(reasoning, UIMessagePart.Text("answer")))

        assertNull(state.editingReasoning)
        state.textContent.setTextAndPlaceCursorAtEnd("new answer")

        assertEquals(listOf(reasoning, UIMessagePart.Text("new answer")), state.getContents())
    }

    @Test
    fun `message with other parts is not editable`() {
        val state = editing(
            listOf(
                UIMessagePart.Reasoning("thought"),
                UIMessagePart.Text("answer"),
                UIMessagePart.Image("file:///image.png"),
            )
        )

        assertNull(state.editingReasoning)
    }

    @Test
    fun `clear input resets reasoning editing`() {
        val state = editing(listOf(UIMessagePart.Reasoning("thought"), UIMessagePart.Text("answer")))

        state.clearInput()

        assertNull(state.editingReasoning)
    }

    @Test
    fun `accepted unchanged full snapshot clears composer`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply {
            setMessageText("queued")
            messageContent = listOf(UIMessagePart.Image("file:///keep.png"))
        }
        val snapshot = state.captureSendSnapshot(owner)

        assertTrue(state.clearAcceptedSnapshot(snapshot, owner, accepted = true))
        assertTrue(state.isEmpty())
    }

    @Test
    fun `rejected submission retains text and attachment`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply {
            setMessageText("keep draft")
            messageContent = listOf(UIMessagePart.Image("file:///keep.png"))
        }
        val snapshot = state.captureSendSnapshot(owner)

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = false))
        assertEquals(snapshot.parts, state.getContents())
    }

    @Test
    fun `accepted text-only guidance preserves attachments`() {
        val owner = Uuid.random()
        val image = UIMessagePart.Image("file:///keep.png")
        val state = ChatInputState().apply {
            setMessageText("guide this run")
            messageContent = listOf(image)
        }
        val snapshot = state.captureSendSnapshot(owner)

        assertTrue(snapshot.canGuide)
        assertTrue(state.clearAcceptedSnapshot(snapshot, owner, accepted = true, textOnly = true))
        assertEquals("", state.textContent.text.toString())
        assertEquals(listOf(image), state.getContents())
    }

    @Test
    fun `late acceptance keeps newer text`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply { setMessageText("old") }
        val snapshot = state.captureSendSnapshot(owner)
        state.setMessageText("new draft")

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = true, textOnly = true))
        assertEquals("new draft", state.textContent.text.toString())
    }

    @Test
    fun `late acceptance keeps edit restored to the same characters`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply { setMessageText("original") }
        val snapshot = state.captureSendSnapshot(owner)
        state.setMessageText("newer")
        state.setMessageText("original")

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = true))
        assertEquals("original", state.textContent.text.toString())
    }

    @Test
    fun `new attachment prevents delayed guidance from clearing anything`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply { setMessageText("guide") }
        val snapshot = state.captureSendSnapshot(owner)
        val image = UIMessagePart.Image("file:///new.png")
        state.messageContent = listOf(image)

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = true, textOnly = true))
        assertEquals("guide", state.textContent.text.toString())
        assertEquals(listOf(image), state.messageContent)
    }

    @Test
    fun `conversation change prevents clearing an accepted old snapshot`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply { setMessageText("old conversation draft") }
        val snapshot = state.captureSendSnapshot(owner)

        assertFalse(state.clearAcceptedSnapshot(snapshot, Uuid.random(), accepted = true))
        assertEquals("old conversation draft", state.textContent.text.toString())
    }

    @Test
    fun `attachment-only snapshot can be queued but not used for guidance`() {
        val state = ChatInputState().apply {
            messageContent = listOf(UIMessagePart.Image("file:///image.png"))
        }
        val snapshot = state.captureSendSnapshot(Uuid.random())

        assertFalse(snapshot.canGuide)
        assertEquals(state.messageContent, snapshot.parts)
    }

    @Test
    fun `entering edit mode prevents stale acceptance from clearing draft`() {
        val owner = Uuid.random()
        val state = ChatInputState().apply { setMessageText("draft") }
        val snapshot = state.captureSendSnapshot(owner)
        state.editingMessage = Uuid.random()

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = true))
        assertFalse(state.captureSendSnapshot(owner).canGuide)
        assertEquals("draft", state.textContent.text.toString())
    }

    @Test
    fun `changing reasoning prevents a stale snapshot clear`() {
        val owner = Uuid.random()
        val state = editing(listOf(UIMessagePart.Reasoning("thought"), UIMessagePart.Text("answer")))
        val snapshot = state.captureSendSnapshot(owner)
        state.editingReasoning!!.setTextAndPlaceCursorAtEnd("new thought")

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = true))
        assertEquals("new thought", state.editingReasoning!!.text.toString())
    }

    @Test
    fun `removing and restoring an attachment is a newer draft version`() {
        val owner = Uuid.random()
        val image = UIMessagePart.Image("file:///keep.png")
        val state = ChatInputState().apply {
            setMessageText("draft")
            messageContent = listOf(image)
        }
        val snapshot = state.captureSendSnapshot(owner)
        state.messageContent = emptyList()
        state.messageContent = listOf(image)

        assertFalse(state.clearAcceptedSnapshot(snapshot, owner, accepted = true))
        assertEquals(snapshot.parts, state.getContents())
    }
}
