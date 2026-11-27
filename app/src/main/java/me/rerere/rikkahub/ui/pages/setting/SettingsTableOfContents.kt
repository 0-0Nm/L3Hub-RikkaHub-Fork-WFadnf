package me.rerere.rikkahub.ui.pages.setting

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Book03
import me.rerere.rikkahub.R

internal data class SettingsTocEntry(
    val key: String,
    @StringRes val titleRes: Int,
    val itemIndex: Int,
)

/** In-app popup only: no service, WindowManager overlay, or overlay permission. */
@Composable
internal fun SettingsTableOfContents(
    entries: List<SettingsTocEntry>,
    onSelect: (SettingsTocEntry) -> Unit,
) {
    // Transient UI state: leaving/recreating this page must not reopen the popup.
    var expanded by remember { mutableStateOf(false) }

    Box {
        SmallFloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = HugeIcons.Book03,
                contentDescription = stringResource(R.string.setting_page_table_of_contents),
            )
        }
        // Material DropdownMenu supplies vertical scrolling and keeps the popup in the window.
        // Do not put a LazyColumn or another verticalScroll inside this scrollable menu.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(208.dp)
                .heightIn(max = 280.dp),
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.titleRes)) },
                    onClick = {
                        expanded = false
                        onSelect(entry)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
