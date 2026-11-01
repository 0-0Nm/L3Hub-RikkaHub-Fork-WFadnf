package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.adaptiveHorizontalPadding
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPreferencesThemePage(embedded: Boolean = false, vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var amoledDarkMode by rememberAmoledDarkMode()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current

    

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .adaptiveHorizontalPadding(16.dp)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CardGroup {
                switchItem(
                    checked = settings.dynamicColor,
                    onCheckedChange = { vm.updateSettings { latest -> latest.copy(dynamicColor = it) } },
                    supportingContent = { Text(stringResource(R.string.setting_page_dynamic_color_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_page_dynamic_color)) },
                )
                item(
                    onClick = { navController.navigate(Screen.SettingTheme) },
                    headlineContent = { Text(stringResource(R.string.setting_page_theme_setting)) },
                    supportingContent = { Text(stringResource(R.string.setting_page_theme_setting_desc)) },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                )
                item(
                    onClick = { navController.navigate(Screen.SettingPalette) },
                    headlineContent = { Text(stringResource(R.string.setting_page_palette)) },
                    supportingContent = { Text(stringResource(R.string.setting_page_palette_desc)) },
                    trailingContent = { Icon(HugeIcons.ArrowRight01, contentDescription = null) },
                )
                switchItem(
                    checked = amoledDarkMode,
                    onCheckedChange = { amoledDarkMode = it },
                    supportingContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title)) },
                )
                switchItem(
                    checked = settings.displaySetting.terminalStyle,
                    onCheckedChange = { enabled ->
                        vm.updateSettings {
                            it.copy(displaySetting = it.displaySetting.copy(terminalStyle = enabled))
                        }
                    },
                    supportingContent = { Text(stringResource(R.string.setting_terminal_style_desc)) },
                    headlineContent = { Text(stringResource(R.string.setting_terminal_style)) },
                )
            }

        }
    }
    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(stringResource(R.string.setting_page_preferences_theme))
                    },
                    navigationIcon = {
                        BackButton()
                    },
                    scrollBehavior = scrollBehavior,
                    colors = CustomColors.topBarColors
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = CustomColors.topBarColors.containerColor
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                content()
            }
        }
    }
}
