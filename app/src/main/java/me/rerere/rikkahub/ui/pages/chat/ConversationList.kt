package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.ui.glassColor
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.mirrorForRtl
import me.rerere.rikkahub.utils.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.Uuid

/**
 * Represents different types of items in the conversation list
 */
sealed class ConversationListItem {
    data class DateHeader(
        val date: LocalDate,
        val label: String,
        val sortOrder: ConversationSortOrder = ConversationSortOrder.UPDATE_TIME,
    ) : ConversationListItem()
    data object PinnedHeader : ConversationListItem()
    data class Item(
        val conversation: Conversation
    ) : ConversationListItem()
}

@Composable
fun ColumnScope.ConversationList(
    current: Conversation,
    conversations: LazyPagingItems<ConversationListItem>,
    conversationJobs: Collection<Uuid>,
    listState: LazyListState,
    collapsedDates: Set<LocalDate> = emptySet(),
    onToggleDate: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    onClick: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {},
    onRename: (Conversation, String) -> Unit = { _, _ -> },
    onDeleteSelected: (List<Conversation>) -> Unit = {},
    onPinSelected: (List<Conversation>) -> Unit = {},
) {
    var hasScrolledToCurrent by remember(current.id) { mutableStateOf(false) }
    // 多选：长按菜单里进入，点条目切换选中
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Uuid>() }
    fun selectedConversations(): List<Conversation> = conversations.itemSnapshotList.items
        .filterIsInstance<ConversationListItem.Item>()
        .map { it.conversation }
        .filter { it.id in selectedIds }
    // 列表顶部若放了 header，会话项在 LazyColumn 中的位置要往后挪一位
    val headerCount = if (header != null) 1 else 0

    LaunchedEffect(current.id, conversations.itemCount, hasScrolledToCurrent) {
        if (hasScrolledToCurrent) return@LaunchedEffect
        val itemIndex = conversations.itemSnapshotList.items.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == current.id
        }
        if (itemIndex >= 0) {
            val currentIndex = itemIndex + headerCount
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
            if (!isVisible) {
                listState.scrollToItem(currentIndex)
            }
            hasScrolledToCurrent = true
        }
    }

    Column(modifier = modifier) {
    AnimatedVisibility(visible = selectionMode) {
        SelectionBar(
            count = selectedIds.size,
            onPin = {
                onPinSelected(selectedConversations())
                selectedIds.clear()
                selectionMode = false
            },
            onDelete = {
                onDeleteSelected(selectedConversations())
                selectedIds.clear()
                selectionMode = false
            },
            onClear = {
                selectedIds.clear()
                selectionMode = false
            },
        )
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.Top,
    ) {
        if (header != null) {
            item(key = "header", contentType = "header") {
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    header()
                }
            }
        }

        if (conversations.itemCount == 0) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Text(
                        text = stringResource(id = R.string.chat_page_no_conversations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(
            count = conversations.itemCount,
            key = conversations.itemKey { item ->
                when (item) {
                    // key 带上排序方式，避免切换排序后列表锚定到另一种排序下的同日期分组而发生跳动
                    is ConversationListItem.DateHeader -> "date_${item.sortOrder}_${item.date}"
                    is ConversationListItem.PinnedHeader -> "pinned_header"
                    is ConversationListItem.Item -> item.conversation.id.toString()
                }
            }
        ) { index ->
            val item = conversations[index]
            // 已经收起的日期分组不渲染下面的会话；标题本身始终显示
            if (item is ConversationListItem.Item) {
                var sectionDate: LocalDate? = null
                for (i in index - 1 downTo 0) {
                    when (val previous = conversations.itemSnapshotList.items.getOrNull(i)) {
                        is ConversationListItem.DateHeader -> {
                            sectionDate = previous.date
                            break
                        }

                        is ConversationListItem.PinnedHeader -> break
                        else -> Unit
                    }
                }
                if (sectionDate != null && sectionDate in collapsedDates) {
                    return@items
                }
            }

            Box(modifier = Modifier.padding(bottom = 8.dp)) {
            when (item) {
                is ConversationListItem.DateHeader -> {
                    DateHeaderItem(
                        label = item.label,
                        collapsed = item.date in collapsedDates,
                        onToggle = { onToggleDate(item.date) },
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.PinnedHeader -> {
                    PinnedHeader(
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.Item -> {
                    ConversationItem(
                        conversation = item.conversation,
                        selected = item.conversation.id == current.id,
                        loading = item.conversation.id in conversationJobs,
                        selectionMode = selectionMode,
                        checked = item.conversation.id in selectedIds,
                        onToggleSelection = {
                            if (item.conversation.id in selectedIds) {
                                selectedIds.remove(item.conversation.id)
                            } else {
                                selectedIds.add(item.conversation.id)
                            }
                        },
                        onEnterSelection = {
                            selectionMode = true
                            if (item.conversation.id !in selectedIds) selectedIds.add(item.conversation.id)
                        },
                        onClick = if (selectionMode) {
                            { conversation ->
                                if (conversation.id in selectedIds) {
                                    selectedIds.remove(conversation.id)
                                } else {
                                    selectedIds.add(conversation.id)
                                }
                                Unit
                            }
                        } else {
                            onClick
                        },
                        onDelete = onDelete,
                        onRegenerateTitle = onRegenerateTitle,
                        onPin = onPin,
                        onMoveToAssistant = onMoveToAssistant,
                        onMoveToFolder = onMoveToFolder,
                        onRename = onRename,
                        modifier = Modifier.animateItem()
                    )
                }

                null -> {
                    // Placeholder for loading state
                }
            }
            }
        }
    }
    }
}

@Composable
private fun DateHeaderItem(
    label: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(glassColor(MaterialTheme.colorScheme.surfaceContainerLow))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (collapsed) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PinnedHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(glassColor(MaterialTheme.colorScheme.surfaceContainerLow))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = HugeIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.pinned_chats),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    selected: Boolean,
    loading: Boolean,
    selectionMode: Boolean = false,
    checked: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    modifier: Modifier = Modifier,
    onDelete: (Conversation) -> Unit = {},
    onRegenerateTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {},
    onRename: (Conversation, String) -> Unit = { _, _ -> },
    onClick: (Conversation) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    // 选中态是玻璃高亮：颜色还是原来那个高亮色，只把不透明度交给玻璃设置。
    // 这里不挂 hazeSource —— 列表每项都糊一遍会掉帧，抽屉底板自己那层玻璃够了。
    val backgroundColor = if (selected) {
        glassColor(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
    } else {
        Color.Transparent
    }
    var showDropdownMenu by remember {
        mutableStateOf(false)
    }
    var renameTarget by remember {
        mutableStateOf<Conversation?>(null)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (selectionMode) onToggleSelection() else onClick(conversation)
                },
                onLongClick = {
                    focusManager.clearFocus(force = true)
                    if (selectionMode) onToggleSelection() else showDropdownMenu = true
                }
            )
            .background(backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = selectionMode) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            imageVector = HugeIcons.Tick01,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }
            Text(
                text = conversation.title.ifBlank { stringResource(id = R.string.chat_page_new_message) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))

            // 置顶图标
            AnimatedVisibility(conversation.isPinned) {
                Icon(
                    imageVector = HugeIcons.Pin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(loading) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.extendColors.green6)
                        .size(4.dp)
                        .semantics {
                            contentDescription = "Loading"
                        }
                )
            }
            DropdownMenu(
                expanded = showDropdownMenu,
                onDismissRequest = { showDropdownMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_page_select_multiple)) },
                    onClick = {
                        onEnterSelection()
                        showDropdownMenu = false
                    },
                    leadingIcon = { Icon(HugeIcons.Tick01, null) },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)
                        )
                    },
                    onClick = {
                        onPin(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin,
                            null
                        )
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.chat_page_rename))
                    },
                    onClick = {
                        renameTarget = conversation
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(HugeIcons.PencilEdit01, null)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(id = R.string.chat_page_regenerate_title))
                    },
                    onClick = {
                        onRegenerateTitle(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(HugeIcons.Refresh01, null)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.chat_page_move_to_assistant))
                    },
                    onClick = {
                        onMoveToAssistant(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(HugeIcons.Forward02, null, modifier = Modifier.mirrorForRtl())
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.chat_page_move_to_folder))
                    },
                    onClick = {
                        onMoveToFolder(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(HugeIcons.Folder01, null)
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(stringResource(id = R.string.chat_page_delete))
                    },
                    onClick = {
                        onDelete(conversation)
                        showDropdownMenu = false
                    },
                    leadingIcon = {
                        Icon(HugeIcons.Delete01, null)
                    }
                )
            }
        }
    }

    renameTarget?.let { target ->
        var title by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.chat_page_edit_title)) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target, title.trim())
                        renameTarget = null
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            },
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = glassColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_page_selected_count, count),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = HugeIcons.Pin,
                contentDescription = stringResource(R.string.pin_chat),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPin)
                    .padding(6.dp)
                    .size(18.dp),
            )
            Spacer(Modifier.size(4.dp))
            Icon(
                imageVector = HugeIcons.Delete01,
                contentDescription = stringResource(R.string.chat_page_delete),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(6.dp)
                    .size(18.dp),
            )
            Spacer(Modifier.size(4.dp))
            Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = stringResource(R.string.chat_page_cancel),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}
