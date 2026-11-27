package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
// Also resolves canSwipe if the agreed core API is supplied as a data.model extension.
import me.rerere.rikkahub.data.model.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.uuid.Uuid

/** The core owns chronology, retention and legacy migration; never sort/filter this list in UI. */
@Composable
fun LiveGuidanceRows(
    entries: List<LiveGuidance>,
    onModeChange: (Uuid, Boolean) -> Unit,
    onNextMessage: (Uuid) -> Unit,
    onCancel: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    conversationId: Uuid? = null,
    actionIds: Set<Uuid> = emptySet(),
) {
    if (entries.isEmpty()) return
    key(conversationId) {
        Column(
            modifier = modifier.fillMaxWidth().heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()).testTag("live_guidance_rows"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEach { item ->
                key(item.id) {
                    Box(Modifier.testTag("live_guidance_row_${item.id}")) {
                        LiveGuidancePanel(
                            guidance = item,
                            onModeChange = onModeChange,
                            onNextMessage = onNextMessage,
                            onCancel = onCancel,
                            actionPending = item.id in actionIds,
                        )
                    }
                }
            }
        }
    }
}

/** Recorded service-owned item only. The main composer remains the only editor. */
@Composable
fun LiveGuidancePanel(
    guidance: LiveGuidance?,
    onModeChange: (Uuid, Boolean) -> Unit,
    onNextMessage: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (Uuid) -> Unit = {},
    actionPending: Boolean = false,
) {
    val item = guidance ?: return
    var showDetails by remember(item.id) { mutableStateOf(false) }
    val persistent = item.mode == LiveGuidanceMode.PERSISTENT
    val canChangeMode = item.runId != null && !item.finishLocked &&
        item.delivery != LiveGuidanceDelivery.NOT_USED && !actionPending
    val swipeEligible = item.delivery == LiveGuidanceDelivery.WAITING && item.canSwipe
    val canSwipe = swipeEligible && !actionPending
    val modeLabel = stringResource(
        if (persistent) R.string.chat_page_busy_persistent else R.string.chat_page_busy_temporary
    )
    val statusLabel = stringResource(
        when (item.delivery) {
            LiveGuidanceDelivery.WAITING -> R.string.chat_page_guidance_compact_waiting
            LiveGuidanceDelivery.DELIVERED -> R.string.chat_page_guidance_compact_delivered
            LiveGuidanceDelivery.NOT_USED -> R.string.chat_page_guidance_compact_not_used
        }
    )
    val cancelLabel = stringResource(R.string.chat_page_guidance_cancel)
    val nextLabel = stringResource(R.string.chat_page_guidance_next_round)
    val releaseCancelLabel = stringResource(R.string.chat_page_guidance_release_cancel)
    val releaseNextLabel = stringResource(R.string.chat_page_guidance_release_next)
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val edgePx = with(density) { 24.dp.toPx() }
    val revealPx = with(density) { 112.dp.toPx() }.coerceAtMost(widthPx * 0.4f).coerceAtLeast(1f)
    val thresholdPx = maxOf(revealPx * 1.6f, widthPx * 0.65f)
    var swipe by remember(item.id, canSwipe, widthPx) { mutableStateOf(LiveGuidanceSwipeState()) }
    val currentCancel by rememberUpdatedState(onCancel)
    val currentNext by rememberUpdatedState(onNextMessage)
    val dispatch: (LiveGuidanceSwipeAction) -> Unit = { action ->
        swipe = swipe.cancel()
        if (canSwipe) {
            when (action) {
                LiveGuidanceSwipeAction.CANCEL -> currentCancel(item.id)
                LiveGuidanceSwipeAction.NEXT_MESSAGE -> currentNext(item.id)
            }
        }
    }
    val currentDispatch by rememberUpdatedState(dispatch)
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("live_guidance_card")
            .onSizeChanged { widthPx = it.width }
            .clip(MaterialTheme.shapes.medium)
            .semantics {
                if (canSwipe) {
                    contentDescription = "$modeLabel · $statusLabel: ${item.content}"
                    customActions = listOf(
                        CustomAccessibilityAction(cancelLabel) {
                            currentDispatch(LiveGuidanceSwipeAction.CANCEL); true
                        },
                        CustomAccessibilityAction(nextLabel) {
                            currentDispatch(LiveGuidanceSwipeAction.NEXT_MESSAGE); true
                        },
                    )
                }
                if (abs(swipe.offset) >= thresholdPx) {
                    stateDescription = if (swipe.offset > 0) releaseCancelLabel else releaseNextLabel
                }
            }
            .pointerInput(item.id, canSwipe, widthPx) {
                if (!canSwipe) return@pointerInput
                try {
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val x = down.position.x
                            // Do not claim system/parent edge gestures or taps on an already exposed button.
                            if (x < edgePx || x > size.width - edgePx ||
                                (swipe.offset > 0 && x < swipe.offset) ||
                                (swipe.offset < 0 && x > size.width + swipe.offset)
                            ) return@awaitEachGesture
                            var total = Offset.Zero
                            var claimed = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
                                    swipe = swipe.cancel()
                                    break
                                }
                                if (!change.pressed) {
                                    if (claimed && change.changedToUpIgnoreConsumed()) {
                                        change.consume()
                                        val release = swipe.release(revealPx, thresholdPx)
                                        swipe = release.state
                                        release.action?.let { currentDispatch(it) }
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                if (!claimed) {
                                    total += delta
                                    val slop = viewConfiguration.touchSlop
                                    // Vertical/diagonal intent remains a normal scroll; consume nothing.
                                    if (abs(total.y) > slop && abs(total.y) >= abs(total.x)) break
                                    if (abs(total.x) <= slop || abs(total.x) <= abs(total.y) * 1.25f) continue
                                    claimed = true
                                    swipe = swipe.begin().dragBy(total.x - sign(total.x) * slop, size.width.toFloat())
                                } else {
                                    swipe = swipe.dragBy(delta.x, size.width.toFloat())
                                }
                                change.consume()
                            }
                        } finally {
                            if (swipe.dragging) swipe = swipe.cancel()
                        }
                    }
                } finally {
                    // Cancellation, resize, delivery or a second pointer must never dispatch an action.
                    swipe = swipe.cancel()
                }
            },
    ) {
        if (canSwipe && swipe.offset != 0f) {
            val right = swipe.offset > 0
            Box(Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier.align(if (right) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                        .width(with(density) { abs(swipe.offset).coerceAtMost(revealPx).toDp() })
                        .fillMaxHeight().clip(MaterialTheme.shapes.medium),
                ) {
                    TextButton(
                        onClick = { currentDispatch(if (right) LiveGuidanceSwipeAction.CANCEL else LiveGuidanceSwipeAction.NEXT_MESSAGE) },
                        modifier = Modifier.width(with(density) { revealPx.toDp() }).fillMaxHeight()
                            .testTag(if (right) "live_guidance_swipe_cancel" else "live_guidance_swipe_next"),
                    ) {
                        Text(if (right) cancelLabel else nextLabel,
                            color = if (right) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().absoluteOffset { IntOffset(swipe.offset.roundToInt(), 0) },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Color is confined to this decorative dot, never the row background. Text carries meaning too.
                Box(Modifier.size(6.dp).background(when (item.delivery) {
                    LiveGuidanceDelivery.WAITING -> MaterialTheme.colorScheme.primary
                    LiveGuidanceDelivery.DELIVERED -> MaterialTheme.colorScheme.tertiary
                    LiveGuidanceDelivery.NOT_USED -> MaterialTheme.colorScheme.outline
                }, CircleShape))
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .toggleable(
                            value = persistent,
                            enabled = canChangeMode,
                            role = Role.Switch,
                            onValueChange = { target -> onModeChange(item.id, target) },
                        )
                        .testTag("live_guidance_mode")
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "$modeLabel · $statusLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(item.content, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(
                    onClick = { showDetails = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("live_guidance_details"),
                ) { Text(stringResource(R.string.chat_page_guidance_details)) }
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(modeLabel) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(when (item.delivery) {
                        LiveGuidanceDelivery.WAITING -> R.string.chat_page_live_guidance_waiting
                        LiveGuidanceDelivery.DELIVERED -> R.string.chat_page_live_guidance_delivered
                        LiveGuidanceDelivery.NOT_USED -> R.string.chat_page_live_guidance_not_used
                    }))
                    Text(item.content, modifier = Modifier.testTag("live_guidance_full_content"))
                    Text(stringResource(R.string.chat_page_guidance_mode_hint))
                    // Ordinary focusable controls are the keyboard/switch-access alternative to swiping.
                    if (swipeEligible) {
                        TextButton(
                            onClick = { currentDispatch(LiveGuidanceSwipeAction.CANCEL); showDetails = false },
                            enabled = !actionPending,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("live_guidance_details_cancel"),
                        ) { Text(cancelLabel) }
                    }
                }
            },
            confirmButton = {
                if (item.delivery == LiveGuidanceDelivery.NOT_USED || swipeEligible) {
                    TextButton(
                        onClick = {
                            if (swipeEligible) currentDispatch(LiveGuidanceSwipeAction.NEXT_MESSAGE)
                            else onNextMessage(item.id)
                            showDetails = false
                        },
                        enabled = !actionPending,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("live_guidance_next"),
                    ) { Text(nextLabel) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.chat_page_guidance_close))
                }
            },
        )
    }
}

/**
 * Durable run-finish history entry. Read-only: the mode was locked with its originating run, and a
 * delivered note has no next-message recovery. It lives in the scrollable history, not in the
 * composer and not as a model/user message.
 */
@Composable
fun ArchivedLiveGuidanceCard(
    entry: me.rerere.rikkahub.data.model.ArchivedLiveGuidance,
    modifier: Modifier = Modifier,
) {
    var showDetails by remember(entry.id) { mutableStateOf(false) }
    val persistent = entry.mode == LiveGuidanceMode.PERSISTENT
    val modeLabel = stringResource(
        if (persistent) R.string.chat_page_busy_persistent else R.string.chat_page_busy_temporary
    )
    Surface(
        modifier = modifier.fillMaxWidth().testTag("archived_live_guidance_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_page_guidance_archived) + " · " + modeLabel +
                        " · " + stringResource(R.string.chat_page_guidance_compact_delivered),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = { showDetails = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("archived_live_guidance_details"),
            ) { Text(stringResource(R.string.chat_page_guidance_details)) }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.chat_page_guidance_archived)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.chat_page_live_guidance_delivered))
                    Text(entry.content, modifier = Modifier.testTag("archived_live_guidance_full_content"))
                    Text(
                        stringResource(
                            if (persistent) {
                                R.string.chat_page_guidance_archived_persistent_hint
                            } else {
                                R.string.chat_page_guidance_archived_temporary_hint
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.chat_page_guidance_close))
                }
            },
        )
    }
}
