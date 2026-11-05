package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.heroSource
import me.rerere.rikkahub.ui.terminal.Switch
import me.rerere.rikkahub.ui.terminal.TopAppBar
import me.rerere.rikkahub.ui.theme.rememberSpatialSpec
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveCornerRadius

private val CardGroupCorner = 20.dp
private val CardGroupItemSpacing = 2.dp
private val CardGroupInnerCorner = 4.dp

private data class CardGroupItem(
    val onClick: (() -> Unit)?,
    val modifier: Modifier,
    // 不为空时这一项点击后展开成对应页面；形状由条目自己算，中间项是 4dp 不是 20dp
    val heroScreen: Screen? = null,
    // false 时这一项收起成 0 高度，用来做「主选项打开后附加选项拉出来」
    val visible: Boolean = true,
    val overlineContent: (@Composable () -> Unit)?,
    val headlineContent: (@Composable () -> Unit)?,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val colors: ListItemColors?,
    // 不为空时是表单项，标题行下方接这块内容
    val formContent: (@Composable ColumnScope.() -> Unit)? = null,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface CardGroupScope {
    fun item(
        onClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        heroScreen: Screen? = null,
        visible: Boolean = true,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        colors: ListItemColors? = null,
        headlineContent: @Composable () -> Unit,
    )

    // 表单项：标题行下方带一块自定义内容（输入框、滑块等），不传标题时整项都是自定义内容
    fun formItem(
        modifier: Modifier = Modifier,
        visible: Boolean = true,
        headlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    )
}

private class CardGroupScopeImpl : CardGroupScope {
    val items = mutableListOf<CardGroupItem>()

    override fun item(
        onClick: (() -> Unit)?,
        modifier: Modifier,
        heroScreen: Screen?,
        visible: Boolean,
        overlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        colors: ListItemColors?,
        headlineContent: @Composable () -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = onClick,
                modifier = modifier,
                heroScreen = heroScreen,
                visible = visible,
                overlineContent = overlineContent,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = colors,
            )
        )
    }

    override fun formItem(
        modifier: Modifier,
        visible: Boolean,
        headlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = null,
                modifier = modifier,
                visible = visible,
                overlineContent = null,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = null,
                formContent = content,
            )
        )
    }
}

// 带开关的项，点整行也能切换
fun CardGroupScope.switchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
) = item(
    onClick = if (enabled) {
        { onCheckedChange(!checked) }
    } else null,
    modifier = modifier,
    visible = visible,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = {
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    },
    headlineContent = headlineContent,
)

// 分组里第 index 项的形状，给不方便走 CardGroup 的列表（比如可拖拽排序的）用。
// 全宽组件模式下圆角为 0，整组是方边。
@Composable
fun cardGroupItemShape(index: Int, count: Int): Shape {
    val corner = adaptiveCornerRadius(CardGroupCorner)
    val inner = adaptiveCornerRadius(CardGroupInnerCorner)
    val top = if (index == 0) corner else inner
    val bottom = if (index == count - 1) corner else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

// 列表项开头带圆形底色的图标
@Composable
fun CardGroupIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .background(animatedContainerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = animatedContentColor,
        )
    }
}

// 标题行右侧显示当前取值的小标签，比如滑块的数值
@Composable
fun CardGroupValue(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            // 等宽数字，拖动滑块时标签宽度不跳
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun CardGroupFormItem(
    item: CardGroupItem,
    formContent: @Composable ColumnScope.() -> Unit,
    count: Int,
    index: Int,
) {
    // 和 CardGroupListItem 一样走 animateDp：附加选项收起时 count 立刻变了，
    // 静态形状会让这张卡的下圆角硬跳一下
    val corner = adaptiveCornerRadius(CardGroupCorner)
    val inner = adaptiveCornerRadius(CardGroupInnerCorner)
    val topCorner by animateDpAsState(
        targetValue = if (index == 0) corner else inner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "formTopCorner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (index == count - 1) corner else inner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "formBottomCorner",
    )
    // 弹簧会过冲，负半径会让 RoundedCornerShape 直接抛异常
    val shape = RoundedCornerShape(
        topStart = topCorner.coerceAtLeast(0.dp),
        topEnd = topCorner.coerceAtLeast(0.dp),
        bottomStart = bottomCorner.coerceAtLeast(0.dp),
        bottomEnd = bottomCorner.coerceAtLeast(0.dp),
    )
    val glass = LocalSettings.current.displaySetting.enableBlurEffect
    Surface(
        modifier = item.modifier
            .fillMaxWidth()
            .glassSurface(shape = shape, baseColor = MaterialTheme.colorScheme.surfaceBright),
        shape = shape,
        color = if (glass) Color.Transparent else CustomColors.listItemColors.containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.headlineContent != null || item.leadingContent != null || item.trailingContent != null) {
                Row(
                    // 和 ListItem 一致：带说明文字时，两侧内容靠上对齐
                    verticalAlignment = if (item.supportingContent != null) Alignment.Top else Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item.leadingContent?.invoke()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        item.headlineContent?.let { headline ->
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge, headline)
                        }
                        item.supportingContent?.let { supporting ->
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium, supporting)
                            }
                        }
                    }
                    item.trailingContent?.invoke()
                }
            }
            formContent()
        }
    }
}

@Composable
private fun CardGroupListItem(
    item: CardGroupItem,
    count: Int,
    index: Int,
) {
    val isFirst = index == 0
    val isLast = index == count - 1

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val corner = adaptiveCornerRadius(CardGroupCorner)
    val innerCorner = adaptiveCornerRadius(CardGroupInnerCorner)
    val topCorner by animateDpAsState(
        targetValue = if (isPressed || count == 1 || isFirst) corner else innerCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (isPressed || count == 1 || isLast) corner else innerCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )

    // 弹簧动画会过冲，圆角可能瞬时为负（尤其全宽模式下目标值是 0），必须夹一下
    val safeTopCorner = topCorner.coerceAtLeast(0.dp)
    val safeBottomCorner = bottomCorner.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(
        topStart = safeTopCorner,
        topEnd = safeTopCorner,
        bottomStart = safeBottomCorner,
        bottomEnd = safeBottomCorner,
    )
    // 毛玻璃底只加在这类「开关行 / 按钮行」上；列表项本身不挂 hazeSource，避免滚动时每项重糊一次
    val glass = LocalSettings.current.displaySetting.enableBlurEffect
    ListItem(
        headlineContent = item.headlineContent ?: {},
        modifier = item.modifier
            .fillMaxWidth()
            .then(
                if (item.heroScreen != null) Modifier.heroSource(item.heroScreen, shape) else Modifier
            )
            .clip(shape)
            .glassSurface(shape = shape, baseColor = MaterialTheme.colorScheme.surfaceBright)
            .then(
                if (item.onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = item.onClick,
                    )
                } else Modifier
            ),
        overlineContent = item.overlineContent,
        supportingContent = item.supportingContent,
        leadingContent = item.leadingContent,
        trailingContent = item.trailingContent,
        // 开了毛玻璃底就把底色交给它，ListItem 自己不再铺一层实色
        colors = if (glass) ListItemDefaults.colors(containerColor = Color.Transparent) else {
            item.colors ?: CustomColors.listItemColors
        },
    )
}

@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    // 不能标 @Composable：那会让 content 变成独立的重组作用域，
    // 外部状态变了只重跑这个 lambda 把 items 填一遍，下面读 items 的 Column 不会重组，
    // 表现就是「点了开关没反应，动一下别的才刷新」
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()

    Column(modifier = modifier) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                ProvideTextStyle(MaterialTheme.typography.titleSmallEmphasized) {
                    Box(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        title()
                    }
                }
            }
        }
        // 关闭动效时直接跳到位，别让显式 spring 绕开这个开关
        val expandSpec = if (LocalSettings.current.displaySetting.disableAnimations) {
            snap<IntSize>()
        } else {
            rememberSpatialSpec<IntSize>()
        }
        val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val count = scope.items.count { it.visible }
        // 收起的条目不占位置，后面的往前挪一格；圆角靠条目自己的 animateDp 平滑跟过去
        var visibleIndex = 0
        scope.items.fastForEachIndexed { _, item ->
            val index = if (item.visible) visibleIndex++ else (count - 1).coerceAtLeast(0)
            AnimatedVisibility(
                visible = item.visible,
                // 从上一项下面拉出来，到位后回弹一下
                enter = expandVertically(expandSpec) + fadeIn(fadeSpec),
                exit = shrinkVertically(expandSpec) + fadeOut(fadeSpec),
            ) {
                Column {
                    val formContent = item.formContent
                    if (formContent != null) {
                        CardGroupFormItem(item = item, formContent = formContent, count = count, index = index)
                    } else {
                        CardGroupListItem(item = item, count = count, index = index)
                    }
                    // 间距放在收起动画里面，跟着条目一起缩；
                    // 不要再加 item.visible：它在收起开始时就已经是 false，
                    // 会让 2dp 瞬间消失、条目才开始慢慢缩，看起来硬跳一下
                    if (index != count - 1) {
                        Spacer(modifier = Modifier.height(CardGroupItemSpacing))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CardGroupPreview() {
    CompositionLocalProvider(LocalSettings provides Settings()) {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text("Card Group")
                    },
                    colors = CustomColors.topBarColors,
                )
            },
            containerColor = CustomColors.topBarColors.containerColor,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = { Text("About") },
                ) {
                    item(
                        headlineContent = { Text("第一项") },
                    )
                    item(
                        headlineContent = { Text("第二项") },
                        supportingContent = { Text("支持文本") },
                    )
                    item(
                        onClick = {},
                        headlineContent = { Text("第三项") },
                        trailingContent = { Text("→") },
                    )
                }
            }
        }
    }
}
