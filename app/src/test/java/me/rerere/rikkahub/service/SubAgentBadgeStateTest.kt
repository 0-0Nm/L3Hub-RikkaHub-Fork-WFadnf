package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * E335 需求 4：主/子任务角标状态机。
 * 优先覆盖：时间（绿暂留/单调时钟）、去重（同 child 多轮/重复事件不双计）、
 * 并发（多 child 同时跑）、失败与恢复。
 */
class SubAgentBadgeStateTest {
    private val a = Uuid.random()
    private val b = Uuid.random()

    private class FakeClock {
        var now = 10_000L
        fun time(): Long = now
        fun advance(millis: Long) { now += millis }
    }

    private fun stateWith(clock: FakeClock, hold: Long = 3_000L) =
        SubAgentBadgeState(clock = clock::time, greenHoldMillis = hold)

    // ---------------- 时间 ----------------

    @Test
    fun `success turns green immediately then orange while other child still runs`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a, b))
        s.recordSuccess(a)
        val snap = s.snapshot()
        assertEquals(SubAgentBadgeState.Phase.GREEN, snap.childPhase)
        assertTrue(snap.greenRemainingMillis!! > 0)
        // 3 秒后还有子任务在跑 → 橙
        clock.advance(3_001L)
        val after = s.snapshot()
        assertEquals(SubAgentBadgeState.Phase.ORANGE, after.childPhase)
        assertNull(after.greenRemainingMillis)
    }

    @Test
    fun `green stays after hold when nothing is running`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = false, runningChildren = setOf(a))
        s.recordSuccess(a)
        s.updateScope(rootRunning = false, runningChildren = emptySet())
        clock.advance(10_000L)
        assertEquals(SubAgentBadgeState.Phase.GREEN, s.snapshot().childPhase)
    }

    @Test
    fun `second completion during green hold refreshes the timer`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a, b))
        s.recordSuccess(a)
        clock.advance(2_500L)
        s.recordSuccess(b)
        clock.advance(2_500L) // 距第一次已 5s，但距第二次只 2.5s
        assertEquals(SubAgentBadgeState.Phase.GREEN, s.snapshot().childPhase)
        clock.advance(501L)
        assertEquals(SubAgentBadgeState.Phase.ORANGE, s.snapshot().childPhase)
    }

    // ---------------- 去重 ----------------

    @Test
    fun `same child success repeated events do not double count`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        s.recordSuccess(a)
        s.recordSuccess(a)
        assertEquals(1, s.snapshot().successCount)
    }

    @Test
    fun `auto continuation rounds count once per child`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        // 同 child 多轮续跑：每轮真实成功都刷新绿暂留，但计数只记唯一 child
        s.recordSuccess(a)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        s.recordSuccess(a)
        assertEquals(1, s.snapshot().successCount)
    }

    @Test
    fun `distinct children count separately`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a, b))
        s.recordSuccess(a)
        s.recordSuccess(b)
        assertEquals(2, s.snapshot().successCount)
    }

    // ---------------- 并发 / 初始 ----------------

    @Test
    fun `initial orange zero while children dispatched but none finished`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a, b))
        val snap = s.snapshot()
        assertEquals(SubAgentBadgeState.Phase.ORANGE, snap.childPhase)
        assertEquals(0, snap.successCount)
        assertTrue(snap.childBadgeVisible)
        // 主角标运行期间隐藏
        assertEquals(false, snap.mainBadgeVisible)
    }

    @Test
    fun `main check appears only when root and scope are done`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        s.recordSuccess(a)
        // 根还没结束 → 主角标仍隐藏
        assertEquals(false, s.snapshot().mainBadgeVisible)
        s.updateScope(rootRunning = false, runningChildren = emptySet())
        // 根结束且范围内全部结束 → 对勾
        assertEquals(true, s.snapshot().mainBadgeVisible)
    }

    @Test
    fun `no children keeps legacy main badge behavior`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = emptySet())
        val snap = s.snapshot()
        assertNull(snap.mainBadgeVisible)
        assertFalse(snap.childBadgeVisible)
    }

    // ---------------- 失败与恢复 ----------------

    @Test
    fun `failure does not count success and keeps orange when scope done`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        s.recordFailure(a)
        s.updateScope(rootRunning = false, runningChildren = emptySet())
        val snap = s.snapshot()
        assertEquals(0, snap.successCount)
        // 失败不算成功：没转绿，任务结束时保持橙
        assertEquals(SubAgentBadgeState.Phase.ORANGE, snap.childPhase)
    }

    @Test
    fun `failed child later succeeding counts once`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        s.recordFailure(a)
        s.recordSuccess(a)
        val snap = s.snapshot()
        assertEquals(1, snap.successCount)
        assertEquals(SubAgentBadgeState.Phase.GREEN, snap.childPhase)
    }

    // ---------------- 批次 / 快照一致性 ----------------

    @Test
    fun `reset clears counts`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a))
        s.recordSuccess(a)
        s.reset()
        assertEquals(0, s.snapshot().successCount)
    }

    @Test
    fun `snapshot colors and count come from the same immutable snapshot`() {
        val clock = FakeClock()
        val s = stateWith(clock)
        s.updateScope(rootRunning = true, runningChildren = setOf(a, b))
        s.recordSuccess(a)
        val snap = s.snapshot()
        // 颜色与计数绑定在同一快照，UI 不能拼出“橙但数字 1”这类错位组合
        if (snap.childPhase == SubAgentBadgeState.Phase.GREEN) {
            assertTrue(snap.successCount >= 1)
        }
        if (snap.successCount == 0) {
            assertEquals(SubAgentBadgeState.Phase.ORANGE, snap.childPhase)
        }
    }
}
