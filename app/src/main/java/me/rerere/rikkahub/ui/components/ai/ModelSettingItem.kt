package me.rerere.rikkahub.ui.components.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope

/**
 * 「默认模型」页里那种模型选项：图标、用途说明，下面一颗显示当前模型的胶囊按钮。
 *
 * 全 app 需要选模型的地方都走这里，省得每个页面各拼一份、样式各长一样。
 */
fun CardGroupScope.modelSettingItem(
    icon: ImageVector,
    @StringRes title: Int,
    state: ModelListState,
    @StringRes description: Int? = null,
    iconContainerColor: Color? = null,
    iconContentColor: Color? = null,
    @StringRes placeholder: Int = R.string.model_list_select_model,
    onClear: (() -> Unit)? = null,
    // 清除按钮只有一个叉，描述得由调用方给，才不至于念出英文 Clear
    @StringRes clearContentDescription: Int? = null,
    // 传 id 不传字符串：调用方要在这里拼 res 名，省得每个调用点各写一份
    @StringRes warning: Int? = null,
) {
    formItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = if (description == null && warning == null) {
            null
        } else {
            {
                Column {
                    description?.let { Text(stringResource(it)) }
                    warning?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        leadingContent = {
            CardGroupIcon(
                icon,
                containerColor = iconContainerColor ?: MaterialTheme.colorScheme.primaryContainer,
                contentColor = iconContentColor ?: MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) {
        ModelField(
            model = state.currentModel,
            onClick = { state.open() },
            placeholder = stringResource(placeholder),
            onClear = onClear,
            clearContentDescription = clearContentDescription?.let { stringResource(it) },
        )
    }
}
