package me.rerere.rikkahub.service

import kotlin.uuid.Uuid

/** Process-lifetime, root-conversation scoped task badges; no network or task side effects. */
internal class SubAgentBadgeState(
    private val clock: () -> Long,
    private val greenHoldMillis: Long = DEFAULT_GREEN_HOLD_MILLIS,
) {
    enum class Phase { HIDDEN, ORANGE, GREEN }
    data class Snapshot(
        val scopeExists: Boolean,
        val mainBadgeVisible: Boolean?,
        val childBadgeVisible: Boolean,
        val childPhase: Phase,
        val successCount: Int,
        val greenRemainingMillis: Long?,
    )
    companion object { const val DEFAULT_GREEN_HOLD_MILLIS = 3_000L }
    private val children = linkedMapOf<Uuid, Boolean>()
    private val succeeded = hashSetOf<Uuid>()
    private val failed = hashSetOf<Uuid>()
    // One latest sequence for each outcome/child, bounded by known tasks rather than rounds.
    private val latestSuccess = hashMapOf<Uuid, Long>()
    private val latestFailure = hashMapOf<Uuid, Long>()
    private var rootRunning = false
    private var greenUntil: Long? = null

    fun updateScope(rootRunning: Boolean, runningChildren: Set<Uuid>) {
        this.rootRunning = rootRunning
        for (id in children.keys.toList()) children[id] = id in runningChildren
        for (id in runningChildren) {
            children[id] = true
            failed.remove(id)
        }
    }

    fun recordSuccess(childId: Uuid, eventSequence: Long, occurredAtMillis: Long = clock()) {
        if (eventSequence <= (latestSuccess[childId] ?: Long.MIN_VALUE)) return
        latestSuccess[childId] = eventSequence
        succeeded.add(childId)
        if (eventSequence > (latestFailure[childId] ?: Long.MIN_VALUE)) failed.remove(childId)
        children.putIfAbsent(childId, false)
        val until = if (occurredAtMillis > Long.MAX_VALUE - greenHoldMillis) Long.MAX_VALUE
            else occurredAtMillis + greenHoldMillis
        greenUntil = maxOf(greenUntil ?: Long.MIN_VALUE, until)
    }

    fun recordFailure(childId: Uuid, eventSequence: Long) {
        if (eventSequence <= (latestFailure[childId] ?: Long.MIN_VALUE)) return
        latestFailure[childId] = eventSequence
        children.putIfAbsent(childId, false)
        if (eventSequence > (latestSuccess[childId] ?: Long.MIN_VALUE)) failed.add(childId)
        // Only jobs determine whether a task is still running; do not hide a live slot.
    }

    fun reset() {
        children.clear(); succeeded.clear(); failed.clear()
        latestSuccess.clear(); latestFailure.clear(); rootRunning = false; greenUntil = null
    }

    fun snapshot(now: Long = clock()): Snapshot {
        val childRunning = children.values.any { it }
        val phase = when {
            children.isEmpty() -> Phase.HIDDEN
            greenUntil?.let { now < it } == true -> Phase.GREEN
            succeeded.isNotEmpty() && !childRunning -> Phase.GREEN
            else -> Phase.ORANGE
        }
        return Snapshot(
            scopeExists = children.isNotEmpty(),
            mainBadgeVisible = if (children.isEmpty()) null else
                !rootRunning && !childRunning && succeeded.isNotEmpty() && failed.isEmpty(),
            childBadgeVisible = children.isNotEmpty(),
            childPhase = phase,
            successCount = succeeded.size,
            greenRemainingMillis = if (phase == Phase.GREEN)
                greenUntil?.let { (it - now).coerceAtLeast(0L) } ?: 0L else null,
        )
    }
}
