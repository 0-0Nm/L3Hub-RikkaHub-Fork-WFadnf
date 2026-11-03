package me.rerere.rikkahub.data.datastore

import android.content.Context
import androidx.core.content.edit

/**
 * 应用锁的本地运行状态。
 *
 * 存在独立的 SharedPreferences 里：它不是用户设置，不进入设置导入导出，也已从系统备份中排除。
 * 冷启动时设置还没从磁盘读完，这里的离开时间要先能判断出该不该锁。
 */
class AppLockStateStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 最近一次离开前台的时刻，0 表示没有待判定的离开。 */
    var leftAt: Long
        get() = preferences.getLong(KEY_LEFT_AT, 0L)
        set(value) = preferences.edit(commit = true) { putLong(KEY_LEFT_AT, value) }

    /** 离开前台后应当锁定的时间点；设置尚未加载完时记 0，等加载后按 [leftAt] 现算。 */
    var pendingLockAt: Long
        get() = preferences.getLong(KEY_PENDING_LOCK_AT, 0L)
        set(value) = preferences.edit(commit = true) { putLong(KEY_PENDING_LOCK_AT, value) }

    /** 已经确定要锁定，只有验证通过或关闭应用锁才会清除。 */
    var locked: Boolean
        get() = preferences.getBoolean(KEY_LOCKED, false)
        set(value) = preferences.edit(commit = true) { putBoolean(KEY_LOCKED, value) }

    fun shouldLock(now: Long = System.currentTimeMillis()): Boolean =
        locked || (pendingLockAt > 0L && now >= pendingLockAt)

    /** 离开前台后的锁定截止时间；优先用离开时算好的值，没有就按设置里的超时现算。 */
    fun lockDeadline(timeoutMinutes: Int): Long {
        if (pendingLockAt > 0L) return pendingLockAt
        if (leftAt <= 0L) return 0L
        return leftAt + timeoutMinutes.coerceAtLeast(0) * 60_000L
    }

    /** 记录一次离开前台；[timeoutMinutes] 为 null 表示设置还没加载完，只记离开时刻。 */
    fun markLeft(timeoutMinutes: Int?, now: Long = System.currentTimeMillis()) {
        val deadline = timeoutMinutes?.let { now + it.coerceAtLeast(0) * 60_000L } ?: 0L
        preferences.edit(commit = true) {
            putLong(KEY_LEFT_AT, now)
            putLong(KEY_PENDING_LOCK_AT, deadline)
        }
    }

    fun markLocked() {
        preferences.edit(commit = true) {
            putBoolean(KEY_LOCKED, true)
            putLong(KEY_PENDING_LOCK_AT, 0L)
            putLong(KEY_LEFT_AT, 0L)
        }
    }

    fun clearPending() {
        preferences.edit(commit = true) {
            putLong(KEY_PENDING_LOCK_AT, 0L)
            putLong(KEY_LEFT_AT, 0L)
        }
    }

    fun clear() {
        preferences.edit(commit = true) {
            putBoolean(KEY_LOCKED, false)
            putLong(KEY_PENDING_LOCK_AT, 0L)
            putLong(KEY_LEFT_AT, 0L)
        }
    }

    private companion object {
        const val FILE_NAME = "rikkahub.app_lock"
        const val KEY_LEFT_AT = "left_at"
        const val KEY_PENDING_LOCK_AT = "pending_lock_at"
        const val KEY_LOCKED = "locked"
    }
}
