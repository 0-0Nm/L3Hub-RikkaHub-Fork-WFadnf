package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * 转场期间不让离场的那一页吃掉点击。
 *
 * navigation3 1.2.0 的 NavDisplay 在 exit transition 结束前，会把离场的 entry 留在
 * AnimatedContent 里继续组合，而且不拦它的指针输入。于是：
 *
 * - pop 时离场页的 zIndex 更高（栈顶那页拿到的是 initialZIndex - 1f），
 *   淡出早就结束了、位移还没收尾，它看不见却盖在上面，点哪里都算点它；
 * - forward 时新页还没盖满屏幕，露出来的那块区域点下去会落到下面那一页。
 *
 * 这里给「目标状态不是 Visible」的 entry 盖一层兄弟节点，在 Initial 阶段消费掉指针事件。
 * 目标状态是 Visible 的是正在进场的那一页，不拦；它自身的位移是 graphicsLayer 做的，
 * 命中区域会跟着视觉位置走，所以进场途中照样能点。
 *
 * 这一层是「吞掉」，不是「让过去」：Compose 的命中测试命中第一个同级节点就停，
 * 被盖住的那一页根本进不了命中链。所以离场期间点到的是空的，不会触发下面那一页的按钮，
 * 也不会触发已经离场那一页的按钮。
 *
 * 代价：纯 fade 的退场（usesHeroTransition() 的那些页）在 pop 时整屏有一整个动画时长的空窗；
 * 带位移的退场会随位移逐步让出可点区域。要缩短这个空窗，只能压短对应的 exit 时长。
 *
 * 判定是 per-scene 的：AnimatedContent 的 content 是一个 Scene，同一 scene 里的多个 entry
 * 共用同一个 transition。多窗格布局里，目标 scene 内的每个 entry 都是 Visible，
 * 不会因为「不是栈顶」被误拦；被拦的只有正在离场的那个 scene 整体。
 */
@Composable
fun <T : Any> rememberNavInputGuardDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator<T> { entry ->
        val leaving = LocalNavAnimatedContentScope.current.transition.targetState !=
            EnterExitState.Visible
        // 必须自己撑满：这一层如果按内容包裹，父级给的是 0..max 的约束，
        // 页面里那些 fillMaxSize 的子节点会跟着缩，转场途中布局会跳一下
        Box(modifier = Modifier.fillMaxSize()) {
            entry.Content()
            if (leaving) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    // 走 Initial：子节点还没拿到事件就吃掉，
                                    // 否则 clickable 在 Main 阶段照样会响应
                                    awaitPointerEvent(PointerEventPass.Initial)
                                        .changes
                                        .forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }
    }
}
