package me.rerere.rikkahub.data.model

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class LiveGuidanceRetryTargetTest {
    @Test
    fun `retry targets trailing user input when no assistant reply was created`() {
        val oldUser = UIMessage.user("old question")
        val oldAssistant = UIMessage.assistant("old answer")
        val trailingUser = UIMessage.user("new question")
        val conversation = Conversation.ofId(Uuid.random()).updateCurrentMessages(
            listOf(oldUser, oldAssistant, trailingUser),
        )

        assertEquals(trailingUser.id, conversation.liveGuidanceRetryTarget())
    }

    @Test
    fun `retry has no target and must not invent a generation for an empty conversation`() {
        assertEquals(null, Conversation.ofId(Uuid.random()).liveGuidanceRetryTarget())
    }
}
