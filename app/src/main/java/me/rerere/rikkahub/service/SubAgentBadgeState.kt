package me.rerere.rikkahub.service

import kotlin.uuid.Uuid

/**
 * 悬浮球主/子任务角标的纯状态机。
 *
 * 数据面约定：
 * - 「哪些子会话在跑」来自生成任务集合（jobs），不是 subAgentState：subAgentState 在完成之后
 *   还可能回到 running（回传结果触发的续跑），它只描述当前那轮，不能当任务是否结束用。
 * - 「这一轮算不算一次成功完成」只认 ChatService.reportSubAgentRound 的判定：
 *   有 cause / 空白回复 / 卡审批 → failed；正常结尾 → completed。
 *   await 与通知两条回传路径在 ChatService 里是同一份账（+1 之后当场 -1），不存在双计来源；
 *   这里再按子会话 ID 对完成数去重，同一份完成事件重复到达不会加第二个数。
 * - 同一 child 自动续跑多轮不冒称新派发任务：完成数按「唯一成功过的子会话」计，
 *   每次真实成功（含续跑轮）都刷新绿色暂留，但不重复 +1。
 *
 * 角标语义（E335 需求 4）：
 * - 主完成角标运行期间隐藏；根主任务及其任务范围（jobs 里属于本根的全部会话）都结束才显示对勾。
 * - 子数字角标：存在子任务且仍在跑时初始橙色 0；每次真实成功完成 +1 并立即转绿 3 秒
 *   （即使其他子任务还在跑）；绿色期间另一次完成刷新 3 秒；到期后还有子任务在跑转回橙色，
 *   否则继续绿色不隐藏。失败/取消不计成功。
 * - 清零只在明确的新任务批次发生；本实现采用约定的局限方案：状态按「当前根会话」范围统计，
 *   根会话切换（悬浮窗目标换根）时重建，同一根内后续批次累计、不清零，也不会清空未完成的孩子。
 *
 * 计时用单调时钟（由构造注入），到期由 UI 侧定时刷新，不依赖进出会话。
 */
internal class SubAgentBadgeState(
    /** 单调时钟源，毫秒；测试可替换 */
    private val clock: () -> Long,
    /** 绿色暂留时长 */
    private val greenHoldMillis: Long = DEFAULT_GREEN_HOLD_MILLIS,
) {
    enum class Phase { HIDDEN, ORANGE, GREEN }

    /**
     * 一次渲染用的不可变快照；颜色与计数都从这一份读，UI 不自行推断。
     * [mainBadgeVisible] 为 null 表示本根没有子任务范围，主角标沿用旧逻辑（错误/审批/回复）。
     */
    data class Snapshot(
        val scopeExists: Boolean,
        val mainBadgeVisible: Boolean?,
        val childBadgeVisible: Boolean,
        val childPhase: Phase,
        val successCount: Int,
        /** GREEN 距离到期还有多久；非 GREEN 为 null */
        val greenRemainingMillis: Long?,
    )

    companion object {
        const val DEFAULT_GREEN_HOLD_MILLIS = 3_000L
    }

    // 已知子会话：id -> 是否在 jobs 里运行
    private val children = LinkedHashMap<Uuid, Boolean>()
    // 真实成功完成过的子会话（按 childId 去重）
    private val succeeded = HashSet<Uuid>()
    // 已确认失败/取消的子会话（不计成功；同 child 之后成功算恢复）
    private val failed = HashSet<Uuid>()
    private var rootRunning = false
    private var phase = Phase.HIDDEN
    private var greenUntil = 0L

    /** 根会话内派发任务集合统计（局限方案：同根累计，不自动清零） */
    fun updateScope(rootRunning: Boolean, runningChildren: Set<Uuid>) {
        this.rootRunning = rootRunning
        for (id in runningChildren) children[id] = true
        for ((id, running) in children) {
            if (running && id !in runningChildren) children[id] = false
        }
        recompute(clock())
    }

    /** 一次真实成功完成（reportSubAgentRound 判定 completed 的那一轮） */
    fun recordSuccess(childId: Uuid) {
        succeeded.add(childId)
        failed.remove(childId)
        children.putIfAbsent(childId, false)
        greenUntil = clock() + greenHoldMillis
        phase = Phase.GREEN
        recompute(clock())
    }

    /** 一次失败/取消（不计成功） */
    fun recordFailure(childId: Uuid) {
        children.putIfAbsent(childId, false)
        children[childId] = false
        failed.add(childId)
        recompute(clock())
    }

    /** 根会话切换 / 悬浮窗关闭：整个范围重建 */
    fun reset() {
        children.clear()
        succeeded.clear()
        failed.clear()
        rootRunning = false
        phase = Phase.HIDDEN
        greenUntil = 0L
        recompute(clock())
    }

    /** 时间推进后的当前快照；GREEN 到期转色在这里判定，不靠进出会话 */
    fun snapshot(now: Long = clock()): Snapshot {
        val anyRunning = anyScopeRunning()
        // 绿色只在暂留窗口内成立；从未成功过（含失败）或暂留到期都转橙
        val phaseNow = when {
            children.isEmpty() -> Phase.HIDDEN
            phase == Phase.GREEN && now < greenUntil -> Phase.GREEN
            succeeded.isNotEmpty() && !anyRunning -> Phase.GREEN
            else -> Phase.ORANGE
        }
        if (phaseNow != Phase.GREEN) greenUntil = 0L
        return Snapshot(
            scopeExists = children.isNotEmpty(),
            mainBadgeVisible = if (children.isEmpty()) null else !anyRunning,
            childBadgeVisible = children.isNotEmpty(),
            childPhase = phaseNow,
            successCount = succeeded.size,
            greenRemainingMillis = if (phaseNow == Phase.GREEN) (greenUntil - now).coerceAtLeast(0) else null,
        )
    }

    /** 根主任务或任何已知子会话仍在运行 */
    private fun anyScopeRunning(): Boolean =
        rootRunning || children.any { (id, running) -> running && id !in failed }

    private fun recompute(now: Long) {
        // 保留下一次 snapshot() 时能用的绿暂留状态
        if (phase == Phase.GREEN && anyScopeRunning() && now >= greenUntil) {
            phase = Phase.ORANGE
        }
    }
}
