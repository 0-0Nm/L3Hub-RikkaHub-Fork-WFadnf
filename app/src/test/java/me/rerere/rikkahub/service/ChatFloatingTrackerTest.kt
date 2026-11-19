package me.rerere.rikkahub.service

import me.rerere.rikkahub.service.ChatFloatingTracker.Action
import me.rerere.rikkahub.service.ChatFloatingTracker.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ChatFloatingTrackerTest {
    private val a = Uuid.random()
    private val b = Uuid.random()

    private fun reduce(vararg actions: Action): TrackingState =
        actions.fold(TrackingState()) { state, action -> state.reduce(action) }

    @Test
    fun `conversation already generating when going to background becomes the target`() {
        val state = reduce(Action.JobsChanged(setOf(a)))
        assertEquals(FloatingChatWindow(a, expanded = false), state.window)
    }

    @Test
    fun `target stays after its generation finishes so the user can reply`() {
        val state = reduce(Action.JobsChanged(setOf(a)), Action.JobsChanged(emptySet()))
        assertEquals(a, state.window?.conversationId)
    }

    @Test
    fun `newly started conversation replaces the target`() {
        val state = reduce(Action.JobsChanged(setOf(a)), Action.JobsChanged(setOf(a, b)))
        assertEquals(b, state.window?.conversationId)
    }

    @Test
    fun `dismissed conversation does not come back until a generation starts`() {
        val dismissed = reduce(Action.JobsChanged(setOf(a, b)), Action.Dismiss)
        assertNull(dismissed.window)
        // 只是别的会话结束了，不算开始新的生成
        assertNull(dismissed.reduce(Action.JobsChanged(setOf(a))).window)
        // 回复推进后重新开始生成
        val restarted = dismissed.reduce(Action.JobsChanged(emptySet())).reduce(Action.JobsChanged(setOf(a)))
        assertEquals(a, restarted.window?.conversationId)
    }

    @Test
    fun `expanding requires a target and dismiss collapses the panel`() {
        assertNull(reduce(Action.Expand).window)
        assertEquals(false, reduce(Action.Expand, Action.JobsChanged(setOf(a))).window?.expanded)

        val expanded = reduce(Action.JobsChanged(setOf(a)), Action.Expand)
        assertEquals(true, expanded.window?.expanded)
        assertEquals(false, expanded.reduce(Action.Collapse).window?.expanded)

        val restarted = expanded.reduce(Action.Dismiss)
            .reduce(Action.JobsChanged(emptySet()))
            .reduce(Action.JobsChanged(setOf(a)))
        assertEquals(false, restarted.window?.expanded)
    }

    @Test
    fun `panel expands when a phone use turn of the target finishes`() {
        val running = reduce(Action.JobsChanged(setOf(a)))
        assertEquals(true, running.reduce(Action.JobsChanged(emptySet())) { it == a }.window?.expanded)
        // 普通聊天结束不打扰
        assertEquals(false, running.reduce(Action.JobsChanged(emptySet())) { false }.window?.expanded)
    }

    @Test
    fun `finishing a conversation that is not the target does not expand`() {
        // b 后开始，是目标；a 结束与悬浮窗无关
        val state = reduce(Action.JobsChanged(setOf(a)), Action.JobsChanged(setOf(a, b)))
            .reduce(Action.JobsChanged(setOf(b))) { true }
        assertEquals(FloatingChatWindow(b, expanded = false), state.window)
    }

    @Test
    fun `dismissed target does not expand when its turn finishes`() {
        val state = reduce(Action.JobsChanged(setOf(a)), Action.Dismiss)
            .reduce(Action.JobsChanged(emptySet())) { true }
        assertNull(state.window)
    }

    // ---- E334：子会话先结束时的目标交接 ----

    private val root = Uuid.random()

    /** parentOf(a)=root（主任务会话）的父子映射 */
    private val parentOfA = { id: Uuid -> if (id == a) root else null }

    @Test
    fun `target hands over to the running parent when a child finishes first`() {
        // 主任务 root 在跑，派发了子任务 a；a 先结束
        val state = reduce(Action.JobsChanged(setOf(root)), Action.JobsChanged(setOf(root, a)))
        val handed = state.reduce(Action.JobsChanged(setOf(root)), parentOf = parentOfA)
        assertEquals(root, handed.window?.conversationId)
        assertEquals(false, handed.window?.expanded)
    }

    @Test
    fun `target is kept when no ancestor is still running`() {
        // 独立会话 a 自己结束：没有父链可交接，保留原目标，收尾语义不变
        val state = reduce(Action.JobsChanged(setOf(a)), Action.JobsChanged(emptySet()))
        assertEquals(a, state.window?.conversationId)
    }

    @Test
    fun `target is kept when the parent has also finished`() {
        // 子任务 a 结束时父任务 root 也已经收尾：不交接，保留已完成的子会话方便直接回复
        val state = reduce(Action.JobsChanged(setOf(root)), Action.JobsChanged(setOf(root, a)))
        val done = state.reduce(Action.JobsChanged(setOf(a)), parentOf = parentOfA)
        assertEquals(a, done.window?.conversationId)
    }

    @Test
    fun `handover walks up past finished middle generations`() {
        // 三代：root -> mid -> leaf。mid 结束时 leaf 还在跑，root 也在跑 → 跳过 mid 交给 root
        val mid = Uuid.random()
        val leaf = Uuid.random()
        val parentOf = { id: Uuid ->
            when (id) {
                leaf -> mid
                mid -> root
                else -> null
            }
        }
        val state = reduce(
            Action.JobsChanged(setOf(root, mid)),
            Action.JobsChanged(setOf(root, mid, leaf)),
        )
        // mid 先结束（leaf、root 还在跑）：不交接（leaf 才是目标）
        val midDone = state.reduce(Action.JobsChanged(setOf(root, leaf)), parentOf = parentOf)
        assertEquals(leaf, midDone.window?.conversationId)
        // leaf 再结束：沿父链找到还在跑的 root
        val leafDone = midDone.reduce(Action.JobsChanged(setOf(root)), parentOf = parentOf)
        assertEquals(root, leafDone.window?.conversationId)
    }

    @Test
    fun `newly started task still wins over handover`() {
        // 子任务 a 结束的同一批 jobs 变化里开始了新会话 b：按"新开始"处理，目标换成 b
        val state = reduce(Action.JobsChanged(setOf(root)), Action.JobsChanged(setOf(root, a)))
        val next = state.reduce(Action.JobsChanged(setOf(root, b)), parentOf = parentOfA)
        assertEquals(b, next.window?.conversationId)
    }

    @Test
    fun `phone use of the handed-over parent expands the panel`() {
        // 子任务 a 结束交接给还在跑的 root；root 这一轮结束后按 Phone Use 展开的仍是 root
        val state = reduce(Action.JobsChanged(setOf(root)), Action.JobsChanged(setOf(root, a)))
        val handed = state.reduce(Action.JobsChanged(setOf(root)), parentOf = parentOfA)
        val done = handed.reduce(Action.JobsChanged(emptySet()), parentOf = parentOfA) { it == root }
        assertEquals(root, done.window?.conversationId)
        assertEquals(true, done.window?.expanded)
    }

    @Test
    fun `cyclic parent chain with no running ancestor terminates and keeps the target`() {
        // 数据异常：parentOf 成环（a -> mid -> root -> mid）。a 结束时环上无在跑祖先：
        // visited 防护让遍历终止并返回 null，按"无可交接祖先"保留原目标，而不是挂起
        val mid = Uuid.random()
        val cyclicParentOf = { id: Uuid ->
            when (id) {
                a -> mid
                mid -> root
                root -> mid
                else -> null
            }
        }
        val state = reduce(Action.JobsChanged(setOf(root, mid)), Action.JobsChanged(setOf(root, mid, a)))
        val kept = state.reduce(Action.JobsChanged(emptySet()), parentOf = cyclicParentOf)
        assertEquals(a, kept.window?.conversationId)
    }

    @Test
    fun `cyclic parent chain still hands over to the running cycle member`() {
        // 同样的环，但 root 还在跑：a 结束沿父链途经已收尾的 mid，命中在跑的 root 正常交接
        val mid = Uuid.random()
        val cyclicParentOf = { id: Uuid ->
            when (id) {
                a -> mid
                mid -> root
                root -> mid
                else -> null
            }
        }
        val state = reduce(Action.JobsChanged(setOf(root, mid)), Action.JobsChanged(setOf(root, mid, a)))
        val handed = state.reduce(Action.JobsChanged(setOf(root)), parentOf = cyclicParentOf)
        assertEquals(root, handed.window?.conversationId)
    }
}
