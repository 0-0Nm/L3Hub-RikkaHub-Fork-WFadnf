package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable

/**
 * 悬浮球的图标。
 *
 * 除 [AppLogo] 外都不带 logo 的轮廓变形：那套形状是按 logo 画的凹多边形，换成任意图片后轮廓不成立，
 * 只能改用圆形底片，生成中的动画也换成呼吸缩放。
 */
@Serializable
sealed class FloatingBubbleIcon {
    /** 应用 logo，生成中按 logo 的轮廓变形 */
    @Serializable
    data object AppLogo : FloatingBubbleIcon()

    /** 内置图标，[name] 对应界面里的预设列表；认不出来的名字回落到默认图标 */
    @Serializable
    data class Preset(val name: String) : FloatingBubbleIcon()

    @Serializable
    data class Emoji(val content: String) : FloatingBubbleIcon()

    /** 从相册选的图片，文件已复制到应用私有目录 */
    @Serializable
    data class Image(val url: String) : FloatingBubbleIcon()
}
