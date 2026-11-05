package me.rerere.rikkahub.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import me.rerere.rikkahub.data.datastore.AnimationSpeed

/** 当前这一帧该用哪一档动画速度，由设置页写入、RouteActivity 提供。 */
val LocalAnimationSpeed = compositionLocalOf { AnimationSpeed.SYSTEM }

/**
 * 页面转场、列表展开这类"位移"动画统一从这里取曲线。
 *
 * SYSTEM 直接用 Material 的 expressive 空间曲线；另外两档换成固定弹簧，
 * 差别只体现在松紧：FAST 收得快，GENTLE 拖得久。
 * 关了动效时上层会把 [LocalAnimationSpeed] 固定成 SYSTEM，
 * 而那时的 motionScheme 已经是 snap，所以这里不用再单独判断。
 */
@Composable
fun <T> rememberSpatialSpec(): FiniteAnimationSpec<T> {
    return when (LocalAnimationSpeed.current) {
        AnimationSpeed.SYSTEM -> MaterialTheme.motionScheme.defaultSpatialSpec()
        AnimationSpeed.FAST -> spring<T>(dampingRatio = 0.9f, stiffness = 900f)
        AnimationSpeed.GENTLE -> spring<T>(dampingRatio = 0.8f, stiffness = 220f)
    }
}
