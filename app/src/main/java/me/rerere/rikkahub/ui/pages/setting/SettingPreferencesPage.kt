package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.UiStyle
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

/**
 * 偏好设置：所有选项都在这一页，按类别分组，不再收进二级菜单。
 */
@Composable
fun SettingPreferencesPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Each section contributes its real heading item followed by its existing embedded content.
    val sections = buildList {
        add("theme" to R.string.setting_page_preferences_theme)
        add("ui" to R.string.setting_page_preferences_ui)
        add("general" to R.string.setting_page_preferences_general)
        add("notification" to R.string.setting_page_preferences_notification)
        if (settings.displaySetting.uiStyle == UiStyle.MIUIX) {
            add("miuix" to R.string.setting_page_miuix_group)
        }
        add("network" to R.string.setting_page_preferences_network)
    }.mapIndexed { index, (key, titleRes) ->
        SettingsTocEntry(key, titleRes, index * 2)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.setting_page_preferences))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        floatingActionButton = {
            SettingsTableOfContents(
                entries = sections,
                onSelect = { section ->
                    scope.launch { listState.animateScrollToItem(section.itemIndex) }
                },
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEach { section ->
                item(key = section.key) {
                    PreferencesSectionTitle(stringResource(section.titleRes))
                }
                item(key = "${section.key}Content") {
                    when (section.key) {
                        "theme" -> SettingPreferencesThemePage(embedded = true)
                        "ui" -> SettingPreferencesUIPage(embedded = true)
                        "general" -> SettingPreferencesGeneralPage(embedded = true)
                        "notification" -> SettingPreferencesNotificationPage(embedded = true)
                        "miuix" -> SettingPreferencesMiuixPage(embedded = true)
                        "network" -> SettingPreferencesNetworkPage(embedded = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferencesSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
}
