package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.model.ExecutionPermission
import me.rerere.workspace.WorkspaceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolsTest {
    private val layout = WorkspaceLayout(
        workspaceDir = "/root/projects",
        writableRoots = listOf("/root/projects"),
    )

    private val targets = listOf(
        WorkspaceTarget(
            id = "651003f8-112e-45ec-bd47-16f4407be0be",
            name = "\"蓝孩\"赠送的服务器",
            layout = layout,
            approvalOverrides = emptyMap(),
            supportsFileWrite = true,
        ),
        WorkspaceTarget(
            id = "fbec1ebe-9980-49cf-8891-d79a0b2c3a64",
            name = "Termux WorkSpace",
            layout = layout,
            approvalOverrides = emptyMap(),
            supportsFileWrite = true,
        ),
    )

    private fun select(workspace: String?) = buildJsonObject {
        if (workspace != null) put("workspace", workspace)
    }.resolveWorkspace(targets)

    @Test
    fun `omitted workspace uses the first target`() {
        assertEquals(targets.first(), select(null))
        assertEquals(targets.first(), select("  "))
    }

    @Test
    fun `exact id and name match`() {
        assertEquals(targets[1], select("fbec1ebe-9980-49cf-8891-d79a0b2c3a64"))
        assertEquals(targets[1], select("Termux WorkSpace"))
        assertEquals(targets.first(), select("\"蓝孩\"赠送的服务器"))
    }

    @Test
    fun `name with dropped or full width quotes still matches`() {
        assertEquals(targets.first(), select("蓝孩\"赠送的服务器"))
        assertEquals(targets.first(), select("“蓝孩”赠送的服务器"))
        assertEquals(targets.first(), select(" '蓝孩‘赠送的服务器 "))
    }

    @Test
    fun `matching ignores case and extra spaces`() {
        assertEquals(targets[1], select("termux   workspace"))
    }

    @Test
    fun `unique id prefix matches`() {
        assertEquals(targets[1], select("fbec1ebe"))
    }

    @Test
    fun `unknown workspace reports available ids`() {
        val error = runCatching { select("不存在的工区") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("651003f8-112e-45ec-bd47-16f4407be0be"))
        assertTrue(error.message!!.contains("fbec1ebe-9980-49cf-8891-d79a0b2c3a64"))
        assertTrue(error.message!!.contains("Use the workspace ID"))
    }

    @Test
    fun `approval path never throws on unknown workspace`() {
        val json = buildJsonObject { put("workspace", "不存在") }
        assertNull(json.resolveWorkspaceOrNull(targets))
    }

    @Test
    fun `single workspace ignores the selector`() {
        val single = listOf(targets.first())
        val json = buildJsonObject { put("workspace", "随便写") }
        assertEquals(targets.first(), json.resolveWorkspace(single))
    }

    private fun withPermission(permission: ExecutionPermission) =
        targets.first().copy(executionPermission = permission)

    @Test
    fun `approval tier keeps the existing per-tool defaults`() {
        val target = withPermission(ExecutionPermission.APPROVAL)
        assertTrue(target.needsApproval("workspace_shell"))
        assertFalse(target.needsApproval("workspace_write_file"))
        assertFalse(target.requiresShellApproval("ls -la"))
        assertTrue(target.requiresShellApproval("npm install"))
    }

    @Test
    fun `read write tier frees file writes but still asks for commands`() {
        val target = withPermission(ExecutionPermission.READ_WRITE)
        assertFalse(target.requiresWriteApproval("workspace_write_file", outsideWritableRoots = false))
        assertTrue(target.requiresWriteApproval("workspace_write_file", outsideWritableRoots = true))
        assertTrue(target.requiresShellApproval("npm install"))
    }

    @Test
    fun `read only tier does not ask and rejects at execution`() {
        val target = withPermission(ExecutionPermission.READ_ONLY)
        assertTrue(target.requiresWriteApproval("workspace_edit_file", outsideWritableRoots = false))
        assertFalse(target.requiresShellApproval("ls -la"))
        assertFalse(target.requiresShellApproval("npm install"))
    }

    @Test
    fun `full tier never asks`() {
        val target = withPermission(ExecutionPermission.FULL)
        assertFalse(target.requiresWriteApproval("workspace_write_file", outsideWritableRoots = true))
        assertFalse(target.requiresShellApproval("sudo rm -rf /"))
        assertFalse(target.needsApproval("workspace_git"))
    }

    @Test
    fun `shell change scan keeps the command and its exit code`() {
        val scan = ShellChangeScan.create("/root/projects", token = "t")
        val wrapped = scan.wrap("echo hi")
        assertTrue(wrapped.contains("\necho hi\n"))
        assertTrue(wrapped.trimEnd().endsWith("exit \$__l3hub_rc"))
    }

    @Test
    fun `blank command is not wrapped`() {
        assertEquals("  ", ShellChangeScan.create("/root/projects", token = "t").wrap("  "))
    }

    @Test
    fun `shell change scan parses paths and creation flag`() {
        val output = listOf(
            "mark\t1700000100",
            "file\t1700000101\t1700000101\t/root/projects/new.kt",
            "file\t0\t1700000200\t/root/projects/old.kt",
            "garbage",
        ).joinToString("\n")
        val files = parseShellChangeScan(output)
        assertEquals(listOf("/root/projects/new.kt", "/root/projects/old.kt"), files.map { it.path })
        assertEquals(true, files[0].created)
        assertNull(files[1].created)
    }
}
