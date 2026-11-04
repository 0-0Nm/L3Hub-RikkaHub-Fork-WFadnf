package me.rerere.rikkahub.data.ai.subagent

import kotlinx.coroutines.CompletableDeferred
import kotlin.uuid.Uuid

/**
 * 主代理在工具调用里等子代理结果时的汇合点。
 *
 * 等待者按子会话 id 登记：[SubAgentRunner] 在启动子会话之前登记，子代理每跑完一轮由
 * ChatService 把正文交给它。等待只是「叫醒」，不接管结果的存放——那一轮照样落盘，
 * 等工具输出真的进了会话，下一次投递会认出这条正文再把它从队列里剔掉。
 * 这样等待被掐断（用户停止）或超时、工具输出还没落库时，落盘那份仍然在，结果不会丢。
 */
class SubAgentWaits {
    private val waiters = mutableMapOf<Uuid, CompletableDeferred<String>>()

    /** 登记一个等待者；同一条子会话上残留的旧等待者立刻收到说明，不再留在表里 */
    fun register(childConversationId: Uuid): CompletableDeferred<String> {
        val deferred = CompletableDeferred<String>()
        val replaced = synchronized(waiters) { waiters.put(childConversationId, deferred) }
        // 在锁外叫醒旧等待者：它会接着跑工具调用，不该拿着这把锁
        replaced?.complete("Another wait on this sub-agent replaced this one.")
        return deferred
    }

    /**
     * 把一轮结果交给等待者。
     *
     * @return true 表示有人接了这一份；false 表示没人等（子代理是后台跑的，或者等待已经
     * 超时/被取消），调用方按原来的通知路径投递即可
     */
    fun complete(childConversationId: Uuid, text: String): Boolean {
        val waiter = synchronized(waiters) { waiters.remove(childConversationId) } ?: return false
        return waiter.complete(text)
    }

    /** 超时或启动失败：只摘掉自己那一个，别误删后来登记的等待者 */
    fun unregister(childConversationId: Uuid, deferred: CompletableDeferred<String>) {
        synchronized(waiters) {
            if (waiters[childConversationId] === deferred) waiters.remove(childConversationId)
        }
    }
}
