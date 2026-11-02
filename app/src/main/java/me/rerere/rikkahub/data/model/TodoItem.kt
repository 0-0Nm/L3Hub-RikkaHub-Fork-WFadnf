package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 会话级任务清单。存在 conversation 表里, 不放进消息历史, 上下文压缩后依然保留。
 */
@Serializable
data class TodoItem(
    val content: String,
    val status: TodoStatus = TodoStatus.PENDING,
    /**
     * 稳定标识，供 todo_update 单条翻状态用。
     * 老数据里没有这个字段（null），读出来照常显示；todo_write 会补上。
     */
    val id: String? = null,
)

@Serializable
enum class TodoStatus {
    @SerialName("pending")
    PENDING,

    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,
}
