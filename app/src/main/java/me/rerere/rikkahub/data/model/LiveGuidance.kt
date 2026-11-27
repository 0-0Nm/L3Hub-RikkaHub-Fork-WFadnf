package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Whether live steering is scoped to the current run or to this conversation. */
@Serializable
enum class LiveGuidanceMode {
    @SerialName("temporary")
    TEMPORARY,

    @SerialName("persistent")
    PERSISTENT,
}

/** Delivered means a provider request carrying the text produced output, not that the model obeyed it. */
@Serializable
enum class LiveGuidanceDelivery {
    @SerialName("waiting")
    WAITING,

    @SerialName("delivered")
    DELIVERED,

    @SerialName("not_used")
    NOT_USED,
}

/**
 * Pure-text live steering. It is persisted inside the existing conversation-config JSON so old
 * backups need no database migration. A single card is replaced only by an explicit new submission.
 */
@Serializable
data class LiveGuidance(
    val id: Uuid = Uuid.random(),
    val content: String,
    val mode: LiveGuidanceMode = LiveGuidanceMode.TEMPORARY,
    val delivery: LiveGuidanceDelivery = LiveGuidanceDelivery.WAITING,
    /** Null only while explicitly armed for the next run. */
    val runId: Uuid? = null,
    val deliveredRunId: Uuid? = null,
    /** The originating run finished; its last accepted mode choice must remain unchanged. */
    val finishLocked: Boolean = false,
) {
    /** Missed entries can be recovered without an active run. Pending actions require a session claim check. */
    val canSwipe: Boolean
        get() = delivery == LiveGuidanceDelivery.NOT_USED

    val actionTarget: LiveGuidanceActionTarget
        get() = LiveGuidanceActionTarget(id, runId, delivery)

    /** Bind an explicitly armed next-message item when its real run starts. */
    fun beginRun(runId: Uuid): LiveGuidance =
        if (!finishLocked && delivery == LiveGuidanceDelivery.WAITING && this.runId == null) {
            copy(runId = runId)
        } else {
            this
        }

    fun isAwaitingOutput(runId: Uuid): Boolean =
        !finishLocked && delivery == LiveGuidanceDelivery.WAITING && this.runId == runId

    /** Persist acknowledgement only after new provider output from a request carrying this item. */
    fun acknowledgeDelivery(runId: Uuid): LiveGuidance =
        if (isAwaitingOutput(runId)) {
            copy(delivery = LiveGuidanceDelivery.DELIVERED, deliveredRunId = runId)
        } else {
            this
        }

    /** Finish locks only the originating run; an old persistent item stays usable in later runs. */
    fun finish(runId: Uuid): LiveGuidance =
        if (!finishLocked && this.runId == runId) {
            copy(
                delivery = if (delivery == LiveGuidanceDelivery.WAITING) {
                    LiveGuidanceDelivery.NOT_USED
                } else {
                    delivery
                },
                finishLocked = true,
            )
        } else {
            this
        }

    /** Mode changes use an explicit target and the same ID, never a stale-snapshot toggle. */
    fun withPersistence(persistent: Boolean, activeRunId: Uuid?): LiveGuidance {
        val nextMode = if (persistent) LiveGuidanceMode.PERSISTENT else LiveGuidanceMode.TEMPORARY
        return if (activeRunId != null && activeRunId == runId && !finishLocked &&
            delivery != LiveGuidanceDelivery.NOT_USED
        ) {
            copy(mode = nextMode)
        } else {
            this
        }
    }

    /** A missed item requires this explicit user action; finishing never auto-arms a later call. */
    fun armForNextRun(): LiveGuidance =
        if (delivery == LiveGuidanceDelivery.NOT_USED) {
            copy(
                delivery = LiveGuidanceDelivery.WAITING,
                runId = null,
                deliveredRunId = null,
                finishLocked = false,
            )
        } else {
            this
        }

    /** A persisted unfinished run cannot survive process death; an armed next run has no ID and stays armed. */
    fun recoverAfterReopen(): LiveGuidance = runId?.let(::finish) ?: this

    fun shouldInject(runId: Uuid): Boolean =
        when (delivery) {
            LiveGuidanceDelivery.WAITING -> isAwaitingOutput(runId)
            LiveGuidanceDelivery.DELIVERED -> mode == LiveGuidanceMode.PERSISTENT || deliveredRunId == runId
            LiveGuidanceDelivery.NOT_USED -> false
        }
}

/** Identity of the state the user acted on, not just the durable ID that survives deferral. */
data class LiveGuidanceActionTarget(
    val id: Uuid,
    val runId: Uuid?,
    val delivery: LiveGuidanceDelivery,
)
