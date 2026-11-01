package me.rerere.rikkahub.ui.components.message.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgsPresentationTest {
    @Test
    fun `json object becomes one field per key`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "workspace_grep",
                argsJson = """{"pattern":"TODO","path":"app/src","max_results":20}""",
            )
        )

        assertEquals(listOf("pattern", "path", "max_results"), fields.map { it.name })
        assertEquals(listOf("TODO", "app/src", "20"), fields.map { it.value })
        assertTrue(fields.all { it.shape == ToolArgValueShape.Text })
    }

    @Test
    fun `nested object becomes json field`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "chart_display",
                argsJson = """{"title":"t","y_axis":{"title":"v","min":0}}""",
            )
        )

        val nested = fields.single { it.name == "y_axis" }
        assertEquals(ToolArgValueShape.Json, nested.shape)
        assertTrue(nested.isMultiline)
        assertTrue(nested.value.contains("\"title\": \"v\""))
    }

    @Test
    fun `multiline string becomes collapsible code field`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "workspace_shell",
                argsJson = """{"command":"cd /tmp\nls -la\npwd\necho done\nexit 0","cwd":"/tmp"}""",
            )
        )

        val command = fields.first { it.name == "command" }
        assertEquals(ToolArgValueShape.Code, command.shape)
        assertEquals("bash", command.language)
        assertEquals(5, command.lineCount)
        assertTrue(command.isCollapsible())
        assertEquals("cd /tmp\nls -la\npwd", ToolArgsPresentation.previewOf(command))

        val cwd = fields.first { it.name == "cwd" }
        assertEquals(ToolArgValueShape.Text, cwd.shape)
        assertFalse(cwd.isCollapsible())
    }

    @Test
    fun `file content argument is highlighted by path extension`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "workspace_write_file",
                argsJson = """{"path":"app/src/Main.kt","text":"fun main() {\n    println(1)\n}"}""",
            )
        )

        val text = fields.single { it.name == "text" }
        assertEquals(ToolArgValueShape.Code, text.shape)
        assertEquals("kotlin", text.language)
    }

    @Test
    fun `long single line value needs its own line`() {
        val path = "/data/data/com.termux/files/home/WorkSpace/projects/very/long/path/to/SomeFile.spec.kt"
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "workspace_read_file",
                argsJson = """{"path":"$path"}""",
            )
        )

        val field = fields.single()
        assertEquals(ToolArgValueShape.Text, field.shape)
        assertTrue(field.needsOwnLine())
        assertTrue(field.isCollapsible())
        assertEquals(path, field.value)
    }

    @Test
    fun `scalar array is joined into one line`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "memory_read",
                argsJson = """{"path":["/topics/food.md","/topics/travel.md"]}""",
            )
        )

        val field = fields.single()
        assertEquals(ToolArgValueShape.Text, field.shape)
        assertEquals("/topics/food.md, /topics/travel.md", field.value)
    }

    @Test
    fun `object array stays a json block`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "todo_write",
                argsJson = """{"todos":[{"content":"看代码","status":"pending"}]}""",
            )
        )

        val todos = fields.single()
        assertEquals(ToolArgValueShape.Json, todos.shape)
        assertTrue(todos.value.contains("\"content\": \"看代码\""))
    }

    @Test
    fun `invalid json returns null`() {
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = """{"command": "ls"""))
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = "not json at all"))
    }

    @Test
    fun `plain text arguments are not parsed`() {
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = "ls -la /tmp"))
    }

    @Test
    fun `empty arguments return null`() {
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = ""))
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = "{}"))
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = "null"))
        assertNull(ToolArgsPresentation.parse(toolName = "workspace_shell", argsJson = """[1,2,3]"""))
    }

    @Test
    fun `preview keeps short values whole`() {
        val fields = requireNotNull(
            ToolArgsPresentation.parse(
                toolName = "workspace_shell",
                argsJson = """{"command":"ls","cwd":"/tmp"}""",
            )
        )

        val command = fields.single { it.name == "command" }
        assertEquals("ls", ToolArgsPresentation.previewOf(command))
        assertEquals(1, command.lineCount)
    }
}
