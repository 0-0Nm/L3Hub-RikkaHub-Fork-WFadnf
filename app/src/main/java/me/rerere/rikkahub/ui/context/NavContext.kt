package me.rerere.rikkahub.ui.context

import android.os.SystemClock
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen

class Navigator(private val backStack: MutableList<NavKey>) {
    // 转场大约 300ms。进入和退出要分开计时：共用一个窗口的话，
    // 刚进页面时按返回会被上一条 navigate 拦掉，看起来就是「点了没反应」。
    private var lastNavigateAt = 0L
    private var lastPopAt = 0L

    private fun throttle(now: Long, last: Long) = now - last >= NAVIGATION_THROTTLE_MS

    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        val now = SystemClock.uptimeMillis()
        if (!throttle(now, lastNavigateAt)) return
        val options = NavigateOptionsBuilder().apply(builder)

        options.popUpToScreen?.let { target ->
            val targetIndex = backStack.indexOfLast { it == target }
            if (targetIndex != -1) {
                val removeFromIndex = if (options.popUpToInclusive) targetIndex else targetIndex + 1
                repeat(backStack.size - removeFromIndex) {
                    backStack.removeLastOrNull()
                }
            }
        }

        // 空操作不占窗口，否则紧接着的第二次点击会被白白吞掉
        if (options.launchSingleTop && backStack.lastOrNull() == screen) {
            return
        }

        lastNavigateAt = now
        backStack.add(screen)
    }

    fun clearAndNavigate(screen: Screen) {
        val now = SystemClock.uptimeMillis()
        if (!throttle(now, lastNavigateAt)) return
        lastNavigateAt = now
        backStack.clear()
        backStack.add(screen)
    }

    fun popBackStack() {
        val now = SystemClock.uptimeMillis()
        if (!throttle(now, lastPopAt)) return
        if (backStack.size > 1) {
            lastPopAt = now
            backStack.removeLastOrNull()
        }
    }
}

private const val NAVIGATION_THROTTLE_MS = 250L

class NavigateOptionsBuilder {
    internal var popUpToScreen: Screen? = null
    internal var popUpToInclusive: Boolean = false
    var launchSingleTop: Boolean = false

    fun popUpTo(screen: Screen, builder: PopUpToBuilder.() -> Unit = {}) {
        val options = PopUpToBuilder().apply(builder)
        popUpToScreen = screen
        popUpToInclusive = options.inclusive
    }
}

class PopUpToBuilder {
    var inclusive: Boolean = false
}

val LocalNavController = compositionLocalOf<Navigator> {
    error("No Navigator provided")
}
