package me.rerere.rikkahub.ui.components.ui.shadow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import me.rerere.rikkahub.ui.components.ui.glassColor
import me.rerere.rikkahub.ui.theme.LocalTerminalMode
import me.rerere.rikkahub.ui.theme.appButtonShadow
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.FilledIconButton as MaterialFilledIconButton
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.FilledTonalIconButton as MaterialFilledTonalIconButton

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialButton(
        onClick = onClick,
        modifier = if (enabled) modifier.appButtonShadow(shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shapes.shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialButton(
        onClick = onClick,
        shapes = shapes,
        modifier = if (enabled) modifier.appButtonShadow(shapes.shape) else modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    // 玻璃底：开了模糊特效是半透明，关掉回到原来的实色（glassColor 内部判断）
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = glassColor(MaterialTheme.colorScheme.secondaryContainer),
    ),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialFilledTonalButton(
        onClick = onClick,
        modifier = if (enabled) modifier.appButtonShadow(shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    shapes: ButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // 玻璃底：开了模糊特效是半透明，关掉回到原来的实色（glassColor 内部判断）
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = glassColor(MaterialTheme.colorScheme.secondaryContainer),
    ),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shapes.shape,
            colors = colors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialFilledTonalButton(
        onClick = onClick,
        shapes = shapes,
        modifier = if (enabled) modifier.appButtonShadow(shapes.shape) else modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.FilledIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialFilledIconButton(
        onClick = onClick,
        modifier = if (enabled) modifier.appButtonShadow(shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    shapes: IconButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.FilledIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shapes.shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialFilledIconButton(
        onClick = onClick,
        shapes = shapes,
        modifier = if (enabled) modifier.appButtonShadow(shapes.shape) else modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialFilledTonalIconButton(
        onClick = onClick,
        modifier = if (enabled) modifier.appButtonShadow(shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    shapes: IconButtonShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (LocalTerminalMode.current) {
        me.rerere.rikkahub.ui.terminal.FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shapes.shape,
            colors = colors,
            interactionSource = interactionSource,
            content = content,
        )
        return
    }
    MaterialFilledTonalIconButton(
        onClick = onClick,
        shapes = shapes,
        modifier = if (enabled) modifier.appButtonShadow(shapes.shape) else modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
