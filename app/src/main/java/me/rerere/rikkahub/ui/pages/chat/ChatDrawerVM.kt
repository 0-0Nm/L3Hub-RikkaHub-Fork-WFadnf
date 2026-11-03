package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.MemoryConsolidationScheduler
import me.rerere.rikkahub.data.datastore.ConversationSortOrder
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.memoryId
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.toLocalString
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.uuid.Uuid

class ChatDrawerVM(
    private val context: Application,
    private val settingsStore: SettingsStore,
    conversationRepo: ConversationRepository,
    private val folderRepo: FolderRepository,
    private val chatService: ChatService,
    private val memoryConsolidationScheduler: MemoryConsolidationScheduler,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val assistantIdFlow = settingsStore.settingsFlow
        .map { it.assistantId }
        .distinctUntilChanged()

    private val sortOrderFlow = settingsStore.settingsFlow
        .map { it.displaySetting.conversationSortOrder }
        .distinctUntilChanged()

    // 当前选中的文件夹筛选，null 表示「未归类」视图
    private val _selectedFolderId = MutableStateFlow<Uuid?>(null)
    val selectedFolderId: StateFlow<Uuid?> = _selectedFolderId.asStateFlow()

    // 收起的日期分组。放在 activity 级 VM 里，切换会话、关闭侧栏后不会被重置；
    // 同时写进 SavedStateHandle，进程重建后和滚动位置保持同一份生命周期
    private val _collapsedDates = MutableStateFlow(
        savedStateHandle.get<LongArray>(KEY_COLLAPSED_DATES)
            ?.map { LocalDate.ofEpochDay(it) }
            ?.toSet()
            .orEmpty()
    )
    val collapsedDates: StateFlow<Set<LocalDate>> = _collapsedDates.asStateFlow()

    // 当前助手的文件夹列表（Room Flow，增删改自动刷新）
    val folders: StateFlow<List<Folder>> = assistantIdFlow
        .flatMapLatest { folderRepo.getFoldersOfAssistant(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前助手的记忆库是否正在后台整理；没开记忆的助手即使共用的全局记忆库在整理也不显示
    val memoryDreaming: StateFlow<Boolean> = settingsStore.settingsFlow
        .map { settings -> settings.getCurrentAssistant().takeIf { it.enableMemory }?.memoryId }
        .distinctUntilChanged()
        .flatMapLatest { memoryId ->
            if (memoryId == null) flowOf(false) else memoryConsolidationScheduler.statusOf(memoryId).map { it.running }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val conversations: Flow<PagingData<ConversationListItem>> =
        combine(assistantIdFlow, _selectedFolderId, sortOrderFlow) { assistantId, folderId, sortOrder ->
            Triple(assistantId, folderId, sortOrder)
        }
            .flatMapLatest { (assistantId, folderId, sortOrder) ->
                val paging = if (folderId == null) {
                    conversationRepo.getUnfiledConversationsOfAssistantPaging(assistantId, sortOrder)
                } else {
                    conversationRepo.getConversationsOfFolderPaging(assistantId, folderId, sortOrder)
                }
                paging.map { pagingData ->
                    pagingData
                        .map { ConversationListItem.Item(it) }
                        .insertSeparators<ConversationListItem.Item, ConversationListItem> { before, after ->
                            when {
                                after == null -> null
                                after.conversation.isPinned -> {
                                    if (before == null) ConversationListItem.PinnedHeader else null
                                }

                                else -> {
                                    // 按周分组（周一开始），减少列表里过密的日期标题
                                    val afterWeek = after.conversation.sortDate(sortOrder).weekStart()
                                    val sameGroup = before != null &&
                                        !before.conversation.isPinned &&
                                        before.conversation.sortDate(sortOrder).weekStart() == afterWeek
                                    if (sameGroup) {
                                        null
                                    } else {
                                        ConversationListItem.DateHeader(
                                            date = afterWeek,
                                            label = getWeekLabel(afterWeek),
                                            sortOrder = sortOrder,
                                        )
                                    }
                                }
                            }
                        }
                }
            }
            .cachedIn(viewModelScope)

    // key 带 v2：早期版本存的是含 header 的绝对下标，语义变了，旧值直接丢弃
    val scrollIndex: Int get() = savedStateHandle["scrollIndexV2"] ?: 0
    val scrollOffset: Int get() = savedStateHandle["scrollOffsetV2"] ?: 0

    init {
        // 助手切换时重置文件夹筛选，回到「聊天」视图，
        // 避免继续显示上一个助手文件夹内的会话（文件夹是助手内分组）
        viewModelScope.launch {
            assistantIdFlow.collect {
                _selectedFolderId.value = null
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        savedStateHandle["scrollIndexV2"] = index
        savedStateHandle["scrollOffsetV2"] = offset
    }

    fun selectFolder(folderId: Uuid?) {
        _selectedFolderId.value = folderId
    }

    fun toggleDateCollapsed(date: LocalDate) {
        _collapsedDates.update { dates ->
            if (date in dates) dates - date else dates + date
        }
        persistCollapsedDates()
    }

    /** 当前会话所在的周被收起时由列表回调，保证打开的会话不会藏在折叠组里。 */
    fun expandDate(date: LocalDate) {
        if (date !in _collapsedDates.value) return
        _collapsedDates.update { it - date }
        persistCollapsedDates()
    }

    private fun persistCollapsedDates() {
        savedStateHandle[KEY_COLLAPSED_DATES] =
            _collapsedDates.value.map { it.toEpochDay() }.toLongArray()
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val assistantId = assistantIdFlow.first()
            folderRepo.createFolder(assistantId, trimmed)
        }
    }

    fun renameFolder(folderId: Uuid, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            folderRepo.renameFolder(folderId, trimmed)
        }
    }

    /**
     * 删除文件夹。若文件夹内有正在生成回复的会话，拒绝删除并返回 false（UI 层据此提示用户）。
     */
    fun deleteFolder(folderId: Uuid): Boolean {
        if (chatService.hasGeneratingConversationInFolder(folderId)) {
            return false
        }
        viewModelScope.launch {
            // 经 ChatService 删除：会同步清空活跃 session 内存态的 folderId，避免整对象保存写回已删文件夹
            chatService.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
        return true
    }

    fun moveConversationToFolder(conversationId: Uuid, folderId: Uuid?) {
        viewModelScope.launch {
            // 经 ChatService 移动：活跃会话会先同步内存态，避免后续整对象保存覆盖 folder_id
            chatService.moveConversationToFolder(conversationId, folderId)
        }
    }

    private fun Conversation.sortDate(sortOrder: ConversationSortOrder): LocalDate {
        val instant = when (sortOrder) {
            ConversationSortOrder.UPDATE_TIME -> updateAt
            ConversationSortOrder.CREATE_TIME -> createAt
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun LocalDate.weekStart(): LocalDate =
        with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private companion object {
        const val KEY_COLLAPSED_DATES = "collapsedDates"
    }

    private fun getWeekLabel(start: LocalDate): String {
        val thisWeek = LocalDate.now().weekStart()
        return when (start) {
            thisWeek -> context.getString(R.string.chat_page_week_this)
            thisWeek.minusWeeks(1) -> context.getString(R.string.chat_page_week_last)
            else -> {
                val end = start.plusDays(6)
                val includeYear = start.year != thisWeek.year
                context.getString(
                    R.string.chat_page_week_range,
                    start.toLocalString(includeYear),
                    end.toLocalString(includeYear),
                )
            }
        }
    }
}
