package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.ui.icons.HeartIcon
import java.util.Locale
import kotlin.uuid.Uuid

private const val UNGROUPED_KEY = "__ungrouped"
private const val SIDEBAR_WIDTH_DP = 88
private const val COMPACT_WIDTH_DP = 360

/**
 * 双列模型选择：左侧是能整条收起的导航栏，右侧是模型卡片。
 *
 * 导航栏按分组列出供应商 Logo，长按分组名可以调整顺序，长按 Logo 进供应商详情；
 * 点左上角的箭头收起导航栏，右侧就拿满整个弹窗宽度。搜索同时匹配模型名和供应商名。
 */
@Composable
fun ModelPickerTwoPane(
    currentModel: Uuid?,
    providers: List<ProviderSetting>,
    modelType: ModelType,
    groups: List<String>,
    assignments: Map<String, String>,
    favoriteIds: List<Uuid>,
    onSelect: (Model) -> Unit,
    onReorderGroups: (List<String>) -> Unit,
    onToggleFavorite: (Uuid) -> Unit,
    onDismiss: () -> Unit,
    onOpenProvider: (ProviderSetting) -> Unit,
    onEditModel: (Model, ProviderSetting) -> Unit,
    onOpenProviderSettings: () -> Unit,
) {
    val compact = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP

    val providersByGroup = remember(providers, assignments) {
        providers.groupBy { provider -> assignments[provider.id.toString()] }
    }
    val orderedGroups = remember(groups, providersByGroup) {
        buildList {
            groups.forEach { name -> if (providersByGroup[name] != null) add(name) }
            providersByGroup.keys
                .filterNotNull()
                .filterNot { it in groups }
                .sorted()
                .forEach { name -> if (name !in this) add(name) }
        }
    }
    // favoriteIds 来自 settings.favoriteModels，保留着用户收藏出来的顺序，别再按供应商顺序重排
    // 直接带出各自的供应商：同一个模型 id 可能挂在多个供应商下，收藏视图不能事后反查
    val favoriteModels = remember(providers, favoriteIds, modelType) {
        val order = favoriteIds.withIndex().associate { (index, id) -> id to index }
        providers.flatMap { provider ->
            provider.models
                .filter { it.type == modelType && it.id in favoriteIds }
                .map { model -> model to provider }
        }.sortedBy { (model, _) -> order[model.id] ?: Int.MAX_VALUE }
    }

    val initialProvider = remember(currentModel, providers) {
        providers.firstOrNull { provider -> provider.models.any { it.id == currentModel } }?.id
            ?: providers.firstOrNull()?.id
    }
    var selectedProviderId by remember(initialProvider) { mutableStateOf(initialProvider) }
    var showFavorites by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var reorderDialog by remember { mutableStateOf(false) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }
    val query = search.trim()

    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val models = remember(query, showFavorites, selectedProvider, providers, modelType, favoriteModels) {
        when {
            // 命中供应商时连同它的所有模型一起列出，命中模型名时只列该模型
            query.isNotBlank() -> providers.flatMap { provider ->
                val providerMatched = provider.name.contains(query, true)
                provider.models
                    .filter { model ->
                        model.type == modelType && (
                            providerMatched ||
                                model.displayName.contains(query, true) ||
                                model.modelId.contains(query, true)
                            )
                    }
                    .map { model -> model to provider }
            }

            showFavorites -> favoriteModels

            selectedProvider != null -> selectedProvider.models
                .filter { it.type == modelType }
                .map { model -> model to selectedProvider }

            else -> emptyList()
        }
    }

    // 取消掉最后一个收藏时收藏入口会消失，继续停在收藏视图就成了一片空白，回到供应商视图
    LaunchedEffect(favoriteModels.isEmpty()) {
        if (favoriteModels.isEmpty()) showFavorites = false
    }

    val listState = rememberLazyListState()
    // 只在切换供应商/切回收藏时定位，收藏切换会重算 models，跟着跳会打断操作
    LaunchedEffect(selectedProviderId, showFavorites) {
        val selectedIndex = models.indexOfFirst { it.first.id == currentModel }
        // 换供应商时列表偏移还留在上一家，没命中就回到顶部
        listState.scrollToItem(if (selectedIndex >= 0) selectedIndex else 0)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 包一层 Box：Row 直接调 AnimatedVisibility 会命中 RowScope 重载，它自己会带权重
        Box {
            AnimatedVisibility(
                visible = sidebarExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally(),
            ) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    ModelPickerSidebar(
                        providersByGroup = providersByGroup,
                        orderedGroups = orderedGroups,
                        showFavoritesEntry = favoriteModels.isNotEmpty(),
                        showFavorites = showFavorites,
                        selectedProviderId = selectedProviderId,
                        onCollapse = { sidebarExpanded = false },
                        onSelectFavorites = {
                            showFavorites = true
                            search = ""
                        },
                        onSelectProvider = { provider ->
                            showFavorites = false
                            search = ""
                            selectedProviderId = provider.id
                        },
                        onOpenProvider = onOpenProvider,
                        onReorderGroups = { reorderDialog = true },
                        onOpenProviderSettings = onOpenProviderSettings,
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (sidebarExpanded) 12.dp else 4.dp, top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!sidebarExpanded) {
                    IconButton(
                        onClick = { sidebarExpanded = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = stringResource(R.string.model_list_expand_sidebar),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.model_list_select_model),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(50),
                leadingIcon = {
                    Icon(
                        imageVector = HugeIcons.Search01,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(
                            onClick = { search = "" },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Cancel01,
                                contentDescription = stringResource(R.string.close),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.model_list_search),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            ) {
                if (providers.isEmpty()) {
                    item(key = "empty_providers") {
                        Text(
                            text = stringResource(R.string.model_list_no_providers),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                } else if (query.isNotBlank() && models.isEmpty()) {
                    item(key = "empty_search") {
                        Text(
                            text = stringResource(R.string.model_list_no_match),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(models, key = { "${it.second.id}:${it.first.id}" }) { (model, provider) ->
                    ModelItem(
                        model = model,
                        providerSetting = provider,
                        select = model.id == currentModel,
                        onSelect = { onSelect(model) },
                        onDismiss = onDismiss,
                        compact = compact,
                        onLongClick = { onEditModel(model, provider) },
                        // 搜索结果把多个供应商拍平了，卡片上得带上供应商名才分得清
                        subtitle = contextWatermark(model, provider.name.takeIf { query.isNotBlank() || showFavorites }),
                        tail = {
                            IconButton(
                                onClick = { onToggleFavorite(model.id) },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = HeartIcon,
                                    contentDescription = "${stringResource(R.string.model_list_favorite)} ${model.displayName}",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (model.id in favoriteIds) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (reorderDialog) {
        GroupOrderDialog(
            groups = orderedGroups,
            onDismiss = { reorderDialog = false },
            onConfirm = { ordered ->
                // ordered 里只有挂了供应商的分组，剩下还留在设置里的空分组补到末尾，别在排序时被删掉
                onReorderGroups(ordered + groups.filterNot { it in ordered })
                reorderDialog = false
            },
        )
    }
}

@Composable
private fun ModelPickerSidebar(
    providersByGroup: Map<String?, List<ProviderSetting>>,
    orderedGroups: List<String>,
    showFavoritesEntry: Boolean,
    showFavorites: Boolean,
    selectedProviderId: Uuid?,
    onCollapse: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectProvider: (ProviderSetting) -> Unit,
    onOpenProvider: (ProviderSetting) -> Unit,
    onReorderGroups: () -> Unit,
    onOpenProviderSettings: () -> Unit,
) {
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier = Modifier
            .width(SIDEBAR_WIDTH_DP.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onCollapse,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 2.dp, top = 2.dp)
                .size(36.dp),
        ) {
            Icon(
                imageVector = HugeIcons.ArrowLeft01,
                contentDescription = stringResource(R.string.model_list_collapse_sidebar),
                modifier = Modifier.size(18.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            if (showFavoritesEntry) {
                item(key = "favorites") {
                    GroupLabel(
                        text = stringResource(R.string.model_list_favorite),
                        selected = showFavorites,
                        collapsed = null,
                        onClick = onSelectFavorites,
                    )
                    ProviderLogoButton(
                        name = stringResource(R.string.model_list_favorite),
                        isFavoriteEntry = true,
                        selected = showFavorites,
                        onClick = onSelectFavorites,
                    )
                }
            }

            orderedGroups.forEach { groupName ->
                item(key = "group:$groupName") {
                    val collapsed = collapsedGroups[groupName] == true
                    GroupLabel(
                        text = groupName,
                        selected = false,
                        collapsed = collapsed,
                        onClick = { collapsedGroups[groupName] = !collapsed },
                        onLongClick = if (orderedGroups.size > 1) onReorderGroups else null,
                    )
                }
                if (collapsedGroups[groupName] != true) {
                    items(providersByGroup[groupName].orEmpty(), key = { it.id }) { provider ->
                        ProviderLogoButton(
                            name = provider.name,
                            isFavoriteEntry = false,
                            selected = !showFavorites && provider.id == selectedProviderId,
                            onClick = { onSelectProvider(provider) },
                            onLongClick = { onOpenProvider(provider) },
                        )
                    }
                }
            }

            val ungrouped = providersByGroup[null].orEmpty()
            if (ungrouped.isNotEmpty()) {
                item(key = "group:$UNGROUPED_KEY") {
                    val collapsed = collapsedGroups[UNGROUPED_KEY] == true
                    GroupLabel(
                        text = stringResource(R.string.setting_provider_group_none),
                        selected = false,
                        collapsed = collapsed,
                        onClick = { collapsedGroups[UNGROUPED_KEY] = !collapsed },
                    )
                }
                if (collapsedGroups[UNGROUPED_KEY] != true) {
                    items(ungrouped, key = { it.id }) { provider ->
                        ProviderLogoButton(
                            name = provider.name,
                            isFavoriteEntry = false,
                            selected = !showFavorites && provider.id == selectedProviderId,
                            onClick = { onSelectProvider(provider) },
                            onLongClick = { onOpenProvider(provider) },
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onOpenProviderSettings,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(40.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Settings02,
                contentDescription = stringResource(R.string.setting_provider_page_title),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun GroupLabel(
    text: String,
    selected: Boolean,
    collapsed: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (collapsed != null) {
            Icon(
                imageVector = if (collapsed) HugeIcons.ArrowRight01 else HugeIcons.ArrowDown01,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderLogoButton(
    name: String,
    isFavoriteEntry: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .size(44.dp)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // 供应商图标自带 contentDescription，再叠一层会被读两遍；收藏入口的心形没有，才补
            .then(if (isFavoriteEntry) Modifier.semantics { contentDescription = name } else Modifier),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isFavoriteEntry) {
                Icon(
                    imageVector = HeartIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                AutoAIIcon(name = name, color = Color.Transparent)
            }
        }
    }
}

@Composable
private fun GroupOrderDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var order by remember(groups) { mutableStateOf(groups) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_list_reorder_groups)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.forEachIndexed { index, name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(HugeIcons.ArrowUp01, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                }
                            },
                            enabled = index < order.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(HugeIcons.ArrowDown01, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(order) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_page_cancel))
            }
        },
    )
}

@Composable
private fun ContextWatermark(model: Model, providerName: String?) {
    val parts = buildList {
        providerName?.let { add(it) }
        if (model.warnContextTokens != null || model.maxContextTokens != null) {
            add("WARN ${formatTokens(model.warnContextTokens)} · MAX ${formatTokens(model.maxContextTokens)}")
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun contextWatermark(model: Model, providerName: String?): (@Composable () -> Unit)? =
    if (providerName != null || model.warnContextTokens != null || model.maxContextTokens != null) {
        { ContextWatermark(model = model, providerName = providerName) }
    } else {
        null
    }

private fun formatTokens(value: Int?): String = when {
    value == null -> "-"
    value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.ROOT, "%.1fK", value / 1_000f)
    else -> value.toString()
}
