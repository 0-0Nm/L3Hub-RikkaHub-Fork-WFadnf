package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassStyleScope
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.effectiveBackgroundEffect
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * 输入框容器的背景效果（模糊或玻璃），未开启时原样返回。
 *
 * 开启后容器本身需要保持透明，[containerColor] 由效果负责铺底和着色。
 */
@Composable
internal fun Modifier.chatInputHaze(
    hazeState: HazeState,
    displaySetting: DisplaySetting,
    containerColor: Color,
    shape: RoundedCornerShape,
    // 顶栏图标按钮这类「玻璃底」读 glassOpacity，输入框和气泡读 blurOpacity
    alpha: Float = displaySetting.blurOpacity,
): Modifier {
    return when (displaySetting.effectiveBackgroundEffect) {
        BackgroundEffectType.OFF -> this

        BackgroundEffectType.BLUR -> {
            val tint = containerColor.copy(
                alpha = containerColor.alpha * alpha,
            )
            hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle.Material3(containerColor = tint) {
                    blurRadius(12.dp)
                },
            )
        }

        BackgroundEffectType.GLASS -> {
            val darkMode = LocalDarkMode.current
            hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = GlassStyle.Material3(
                    containerColor = containerColor,
                    tint = containerColor.copy(
                        alpha = containerColor.alpha * alpha,
                    ),
                ) {
                    appearanceResponse(dark = darkMode)
                    // Keep background text from competing with the input text.
                    optics(
                        GlassDefaults.optics.copy(
                            blurRadius = OpticalSizeValue.Fixed(16.dp),
                            depth = OpticalSizeValue.Fixed(0.5f),
                        )
                    )
                    shape(shape)
                },
            )
        }
    }
}

// Haze 按系统夜间模式选玻璃的明暗预设，应用内单独设置主题时会选错（深色主题下被提亮发白），
// 所以按应用主题显式写入。深色数值对应库里 internal 的 GlassStyle.regularDark。
private fun GlassStyleScope.appearanceResponse(dark: Boolean) {
    if (dark) {
        specularIntensity(0.38f)
        edgeShadow(Color.Black.copy(alpha = 0.32f))
        ambientResponse(0.08f)
        contrast(0.08f)
        whitePoint(-0.22f)
        chromaMultiplier(1.1f)
    } else {
        specularIntensity(GlassDefaults.specularIntensity)
        edgeShadow(GlassDefaults.edgeShadow)
        ambientResponse(GlassDefaults.ambientResponse)
        contrast(GlassDefaults.contrast)
        whitePoint(GlassDefaults.whitePoint)
        chromaMultiplier(GlassDefaults.chromaMultiplier)
    }
}
