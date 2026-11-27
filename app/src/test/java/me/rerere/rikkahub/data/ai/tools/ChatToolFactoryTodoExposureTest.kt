package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ChatToolFactoryTodoExposureTest {
    private val conversationId: Uuid = Uuid.random()
    private val toolModel = Model(abilities = listOf(ModelAbility.TOOL))
    private val plainModel = Model()

    @Test
    fun `tool-capable model exposes checklist without a workspace`() {
        assertTrue(shouldExposeTodoTools(conversationId, Assistant(), toolModel))
    }

    @Test
    fun `plan mode without tool ability does not force the checklist`() {
        val assistant = Assistant(planMode = true)
        assertFalse(shouldExposeTodoTools(conversationId, assistant, plainModel))
    }

    @Test
    fun `existing workspace flow is preserved even without tool ability`() {
        val assistant = Assistant(workspaceIds = setOf(Uuid.random()))
        assertTrue(shouldExposeTodoTools(conversationId, assistant, plainModel))
    }

    @Test
    fun `no conversation means no checklist`() {
        assertFalse(shouldExposeTodoTools(null, Assistant(), toolModel))
    }
}
