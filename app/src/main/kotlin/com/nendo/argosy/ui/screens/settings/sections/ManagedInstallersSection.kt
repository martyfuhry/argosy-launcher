package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.ManagedInstallerRow
import com.nendo.argosy.ui.screens.settings.ManagedInstallersState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class InstallerItem(val key: String) {
    data object AddRepo : InstallerItem("addRepo")
    data class Entry(val row: ManagedInstallerRow) : InstallerItem("installer-${row.id}")
    data class ImeEnable(val row: ManagedInstallerRow) : InstallerItem("ime-enable-${row.id}")
    data class ImeSelect(val row: ManagedInstallerRow) : InstallerItem("ime-select-${row.id}")
}

internal fun installerItems(state: ManagedInstallersState): List<InstallerItem> = buildList {
    add(InstallerItem.AddRepo)
    state.rows.forEach { row ->
        add(InstallerItem.Entry(row))
        if (row.installed && row.packageName != null && isKeyboardRow(row)) {
            if (!row.imeEnabled) add(InstallerItem.ImeEnable(row))
            if (row.imeEnabled && !row.imeActive) add(InstallerItem.ImeSelect(row))
        }
    }
}

private fun isKeyboardRow(row: ManagedInstallerRow): Boolean =
    row.packageName == com.nendo.argosy.data.installer.CONTROLLER_KEYBOARD_PACKAGE

internal fun installersMaxFocusIndex(state: ManagedInstallersState): Int =
    (installerItems(state).size - 1).coerceAtLeast(0)

internal fun installerItemAtFocusIndex(index: Int, state: ManagedInstallersState): InstallerItem? =
    installerItems(state).getOrNull(index)

@Composable
fun ManagedInstallersSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val state = uiState.managedInstallers
    val items = remember(state.rows, state.busyId) { installerItems(state) }

    fun isFocused(index: Int): Boolean = uiState.focusedIndex == index

    SectionPaneLayout(
        items = items,
        sections = emptyList(),
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { it },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { false },
        onSectionTap = {},
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        val index = items.indexOf(item)
        when (item) {
            InstallerItem.AddRepo -> ActionPreference(
                title = stringResource(R.string.settings_installers_add_title),
                subtitle = stringResource(R.string.settings_installers_add_subtitle),
                icon = Icons.Default.Add,
                isFocused = isFocused(index),
                onClick = { viewModel.openInstallerAddModal() }
            )

            is InstallerItem.Entry -> {
                val row = item.row
                val busy = state.busyId == row.id
                val statusRes = state.statusRes
                val subtitle = when {
                    busy && statusRes == R.string.settings_installers_status_downloading ->
                        stringResource(statusRes, (state.busyProgress * 100).toInt())
                    busy && statusRes != null -> stringResource(statusRes)
                    row.updateAvailable -> stringResource(
                        R.string.settings_installers_subtitle_update,
                        row.latestSeenTag ?: ""
                    )
                    row.installed -> stringResource(
                        R.string.settings_installers_subtitle_installed,
                        row.tagAtInstall ?: ""
                    )
                    else -> row.repoLabel
                }
                ActionPreference(
                    title = row.displayName,
                    subtitle = subtitle,
                    icon = if (row.locked) Icons.Default.Lock else Icons.Default.Download,
                    isFocused = isFocused(index),
                    isEnabled = !busy,
                    spinIcon = busy,
                    badge = if (row.updateAvailable) {
                        stringResource(R.string.settings_installers_badge_update)
                    } else {
                        null
                    },
                    onClick = { viewModel.installManagedInstaller(row.id) }
                )
            }

            is InstallerItem.ImeEnable -> ActionPreference(
                title = stringResource(R.string.settings_installers_ime_enable_title),
                subtitle = stringResource(R.string.settings_installers_ime_enable_subtitle),
                icon = Icons.Default.Keyboard,
                isFocused = isFocused(index),
                onClick = { viewModel.openInstallerImeSettings() }
            )

            is InstallerItem.ImeSelect -> ActionPreference(
                title = stringResource(R.string.settings_installers_ime_select_title),
                subtitle = stringResource(R.string.settings_installers_ime_select_subtitle),
                icon = Icons.Default.Keyboard,
                isFocused = isFocused(index),
                onClick = { viewModel.showInstallerImePicker() }
            )
        }
    }
}
