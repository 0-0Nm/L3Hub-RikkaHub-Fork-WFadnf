package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Image03
import me.rerere.hugeicons.stroke.Text
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.terminal.Card
import me.rerere.rikkahub.ui.terminal.IconButton
import me.rerere.rikkahub.ui.terminal.TextButton
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

class ModelListState internal constructor(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    groups: List<String> = emptyList(),
    assignments: Map<String, String> = emptyMap(),
) {
    var modelId by mutableStateOf(modelId)
        private set

    var providers by mutableStateOf(providers)
        private set

    var type by mutableStateOf(type)
        private set

    var visible by mutableStateOf(false)
        private set

    var groups by mutableStateOf(groups)
        private set

    var assignments by mutableStateOf(assignments)
        private set

    val currentModel: Model?
        get() = modelId?.let { providers.findModelById(it) }

    val filteredProviders: List<ProviderSetting>
        get() {
            val rank = groups.withIndex().associate { (index, name) -> name to index }
            return providers.fastFilter { provider ->
                provider.enabled && provider.models.fastAny { model -> model.type == type }
            }.sortedBy { provider ->
                val group = assignments[provider.id.toString()]
                when {
                    group == null -> groups.size + 1
                    rank[group] != null -> rank.getValue(group)
                    else -> groups.size
                }
            }
        }

    fun groupOf(provider: ProviderSetting): String? = assignments[provider.id.toString()]

    fun open() {
        visible = true
    }

    fun close() {
        visible = false
    }

    internal fun update(
        modelId: Uuid?,
        providers: List<ProviderSetting>,
        type: ModelType,
        groups: List<String> = this.groups,
        assignments: Map<String, String> = this.assignments,
    ) {
        this.modelId = modelId
        this.providers = providers
        this.type = type
        this.groups = groups
        this.assignments = assignments
    }
}

@Composable
fun rememberModelListState(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
): ModelListState {
    val settings = me.rerere.rikkahub.ui.context.LocalSettings.current
    return remember {
        ModelListState(
            modelId = modelId,
            providers = providers,
            type = type,
        )
    }.also {
        it.update(
            modelId = modelId,
            providers = providers,
            type = type,
            groups = settings.providerGroups,
            assignments = settings.providerGroupAssignments,
        )
    }
}

@Composable
fun ModelSelector(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onSelect: (Model) -> Unit
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = type,
    )

    ModelSelectorButton(
        state = state,
        modifier = modifier,
        onlyIcon = onlyIcon,
        allowClear = allowClear,
        onClear = { onSelect(Model()) },
    )

    ModelListSheet(
        state = state,
        onSelect = onSelect,
    )
}

@Composable
internal fun ModelSelectorButton(
    state: ModelListState,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onClear: () -> Unit = {},
) {
    val model = state.currentModel

    if (!onlyIcon) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    state.open()
                },
                modifier = modifier
            ) {
                model?.modelId?.let {
                    AutoAIIcon(
                        it, Modifier
                            .padding(end = 4.dp)
                            .size(36.dp),
                        color = Color.Transparent
                    )
                }
                Text(
                    text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (allowClear && model != null) {
                IconButton(
                    onClick = onClear,
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = "Clear"
                    )
                }
            }
        }
    } else {
        IconButton(
            onClick = {
                state.open()
            },
        ) {
            if (model != null) {
                AutoAIIcon(
                    modifier = Modifier.size(36.dp),
                    name = model.modelId,
                    color = Color.Transparent
                )
            } else {
                Icon(
                    imageVector = HugeIcons.Brain02,
                    contentDescription = stringResource(R.string.setting_model_page_chat_model),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// 显示当前模型的胶囊按钮，样式和 Select 保持一致，点击后由调用方打开模型列表
// 给了 onClear 时，选了模型后右侧显示清除按钮；placeholder 是没选模型时显示的文字
@Composable
internal fun ModelField(
    model: Model?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.model_list_select_model),
    onClear: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        tonalElevation = 4.dp,
        shape = CircleShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (model != null) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier.size(24.dp),
                    color = Color.Transparent,
                )
            }
            Text(
                text = model?.displayName ?: placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onClear != null && model != null) {
                Icon(
                    imageVector = HugeIcons.Cancel01,
                    contentDescription = "Clear",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                )
            } else {
                Icon(
                    imageVector = HugeIcons.ArrowDown01,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
fun ModelListSheet(
    state: ModelListState,
    onSelect: (Model) -> Unit,
) {
    if (!state.visible) return

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    fun dismiss() {
        coroutineScope.launch {
            sheetState.hide()
            state.close()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            state.close()
        },
        sheetState = sheetState,
        // 列表本身要上下滑，sheet 的下拉手势很容易在滚动时误触关闭；
        // 只留两种关法：点弹窗外部，或点右上角 X
        sheetGesturesEnabled = false,
        // 手势关了就别留把手，免得看起来能拖却拖不动
        dragHandle = null,
    ) {
        ModelListContent(
            state = state,
            onSelect = onSelect,
            onDismiss = { dismiss() },
        )
    }
}

/**
 * 模型列表的内容，不含外层 sheet。
 *
 * 平时由 [ModelListSheet] 套一个 sheet；单行输入模式下从思考强度面板切过来时，
 * 直接塞进那个已经打开的 sheet 里，省得关一个再开一个。
 */
@Composable
fun ModelListContent(
    state: ModelListState,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxHeight(0.8f)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 关闭按钮在 ModelPickerTwoPane 的标题行右侧，这里不再重复放一个
        ModelPickerTwoPane(
            currentModel = state.modelId,
            providers = state.filteredProviders,
            modelType = state.type,
            groups = settings.providerGroups,
            assignments = settings.providerGroupAssignments,
            favoriteIds = settings.favoriteModels,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
            onReorderGroups = { ordered ->
                coroutineScope.launch {
                    settingsStore.update { it.copy(providerGroups = ordered) }
                }
            },
            onToggleFavorite = { modelId ->
                coroutineScope.launch {
                    settingsStore.update { current ->
                        current.copy(
                            favoriteModels = if (modelId in current.favoriteModels) {
                                current.favoriteModels - modelId
                            } else {
                                current.favoriteModels + modelId
                            }
                        )
                    }
                }
            },
            onDismiss = onDismiss,
            onOpenProvider = { provider ->
                onDismiss()
                navController.navigate(Screen.SettingProviderDetail(provider.id.toString()))
            },
            onEditModel = { model, provider ->
                onDismiss()
                navController.navigate(
                    Screen.ModelEditor(
                        providerId = provider.id.toString(),
                        modelId = model.id.toString(),
                    )
                )
            },
            onOpenProviderSettings = {
                onDismiss()
                navController.navigate(Screen.SettingProvider)
            },
        )
    }
}

// 模型卡片：左侧图标方块，右侧名称、上下文水位和标签，tail 放收藏这类行尾操作
// compact 给窄屏用，图标缩小、标签独占一行，名称就不会被行尾按钮挤没
@Composable
internal fun ModelItem(
    model: Model,
    providerSetting: ProviderSetting,
    select: Boolean,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    tail: @Composable RowScope.() -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
) {
    val navController = LocalNavController.current
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = Modifier.combinedClickable(
        enabled = true,
        onLongClick = onLongClick ?: {
            onDismiss()
            navController.navigate(
                Screen.SettingProviderDetail(
                    providerSetting.id.toString()
                )
            )
        },
        onClick = { onSelect(model) },
        interactionSource = interactionSource,
        indication = LocalIndication.current
    )
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (select) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (select) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (compact) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(clickable),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModelIconBadge(model = model, compact = true)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ModelName(model = model)
                            subtitle?.invoke()
                        }
                    }
                    ModelTags(model = model)
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(clickable),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModelIconBadge(model = model, compact = false)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ModelName(model = model)
                        subtitle?.invoke()
                        ModelTags(model = model)
                    }
                }
            }
            tail()
        }
    }
}

@Composable
private fun ModelName(model: Model) {
    Text(
        text = model.displayName,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ModelIconBadge(model: Model, compact: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        AutoAIIcon(
            name = model.modelId,
            modifier = Modifier
                .padding(if (compact) 4.dp else 5.dp)
                .size(if (compact) 26.dp else 36.dp),
        )
    }
}

@Composable
private fun ModelTags(model: Model) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModelProviderOverrideTag(model = model)

        ModelTypeTag(model = model)

        ModelModalityTag(model = model)

        ModelAbilityTag(model = model)
    }
}

// 模型的供应商覆盖来自另一份配置，这里只露出它的类型名，跟供应商详情页的显示保持一致
@Composable
fun ModelProviderOverrideTag(model: Model) {
    val providerOverwrite = model.providerOverwrite ?: return
    Tag(
        type = TagType.INFO
    ) {
        Text(
            text = providerOverwrite.javaClass.simpleName ?: providerOverwrite.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ModelTypeTag(model: Model) {
    Tag(
        type = TagType.INFO
    ) {
        Text(
            text = stringResource(
                when (model.type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            )
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    Tag(
        type = TagType.SUCCESS
    ) {
        model.inputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
        Icon(
            imageVector = HugeIcons.ArrowRight01,
            contentDescription = null,
            modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
        )
        model.outputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> HugeIcons.Text
                    Modality.IMAGE -> HugeIcons.Image03
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
    }
}

@Composable
fun ModelAbilityTag(model: Model) {
    model.abilities.fastForEach { ability ->
        when (ability) {
            ModelAbility.TOOL -> {
                Tag(
                    type = TagType.WARNING
                ) {
                    Icon(
                        imageVector = HugeIcons.Tools,
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
                    )
                }
            }

            ModelAbility.REASONING -> {
                Tag(
                    type = TagType.INFO
                ) {
                    Icon(
                        painter = painterResource(R.drawable.deepthink),
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                    )
                }
            }
        }
    }
}
