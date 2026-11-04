package me.rerere.rikkahub.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceCwdTest {
    @Test
    fun `relative path stays relative`() {
        assertEquals("sub", WorkspaceCwd.normalize("/workspace", "sub"))
        assertEquals("a/b", WorkspaceCwd.normalize("/home/u/proj", "a/b/"))
    }

    @Test
    fun `legacy absolute path is stripped of the workspace prefix`() {
        assertEquals("sub", WorkspaceCwd.normalize("/workspace", "/workspace/sub"))
        assertEquals("sub", WorkspaceCwd.normalize("/home/u/proj", "/home/u/proj/sub"))
        assertNull(WorkspaceCwd.normalize("/workspace", "/workspace"))
        assertNull(WorkspaceCwd.normalize("/workspace", "  "))
        assertNull(WorkspaceCwd.normalize("/workspace", "."))
    }

    @Test
    fun `absolute path outside the workspace is treated as unset`() {
        assertNull(WorkspaceCwd.normalize("/workspace", "/home/u/proj/sub"))
    }

    @Test
    fun `absolute joins the workspace root`() {
        assertEquals("/workspace", WorkspaceCwd.absolute("/workspace", null))
        assertEquals("/workspace/sub", WorkspaceCwd.absolute("/workspace/", "sub"))
        assertEquals("/home/u/proj/sub", WorkspaceCwd.absolute("/home/u/proj", "/sub/"))
        assertEquals("/sub", WorkspaceCwd.absolute("/", "sub"))
    }

    @Test
    fun `remote agents path no longer doubles the workspace prefix`() {
        val workspaceDir = "/home/u/proj"
        val stored = "/home/u/proj/sub"
        val relative = WorkspaceCwd.normalize(workspaceDir, stored)
        assertEquals("/home/u/proj/sub/AGENTS.md", "${WorkspaceCwd.absolute(workspaceDir, relative)}/AGENTS.md")
    }
}
