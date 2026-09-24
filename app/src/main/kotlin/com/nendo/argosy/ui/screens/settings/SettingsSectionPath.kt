package com.nendo.argosy.ui.screens.settings

internal const val SECTION_PATH_KEY = "settings_section_path"
internal const val SECTION_FOCUS_KEY = "settings_section_focus"

/**
 * Sections whose content is fully rebuilt by entering them, with nothing chosen on the way in.
 * A section that shows one platform, core, game or shader is absent, so resuming stops at its
 * parent.
 */
internal val RESUMABLE_SECTIONS: Set<SettingsSection> = setOf(
    SettingsSection.MAIN,
    SettingsSection.ACCOUNTS,
    SettingsSection.ROMM,
    SettingsSection.SAVES,
    SettingsSection.SYNC_SETTINGS,
    SettingsSection.STEAM_SETTINGS,
    SettingsSection.JELLYFIN,
    SettingsSection.RETRO_ACHIEVEMENTS,
    SettingsSection.STORAGE,
    SettingsSection.STORAGE_GAMES,
    SettingsSection.STORAGE_MEDIA,
    SettingsSection.PLAY_TIME,
    SettingsSection.BIOS,
    SettingsSection.THEME,
    SettingsSection.AUDIO,
    SettingsSection.THEME_SOUNDS,
    SettingsSection.THEME_MUSIC,
    SettingsSection.THEME_FONTS,
    SettingsSection.THEME_BACKDROP,
    SettingsSection.INTERFACE,
    SettingsSection.CONTROLLER_GRIP,
    SettingsSection.HOME_SCREEN,
    SettingsSection.LIBRARY_VIEW,
    SettingsSection.DISPLAYS,
    SettingsSection.SCREENS,
    SettingsSection.AMBIENT_LED,
    SettingsSection.NAVIGATION,
    SettingsSection.PLATFORMS,
    SettingsSection.BUILTIN_EMULATOR,
    SettingsSection.SOCIAL,
    SettingsSection.PERMISSIONS,
    SettingsSection.DRIVERS,
    SettingsSection.MANAGED_INSTALLERS,
    SettingsSection.ABOUT
)

internal fun SettingsUiState.sectionPath(): List<SettingsNavEntry> =
    backStack + SettingsNavEntry(currentSection, focusedIndex)

/**
 * The longest leading run of a recorded section path that can be re-entered, oldest first.
 * Empty when the first entry cannot be, or when the names and focus indices disagree in length.
 */
internal fun resumableSectionPath(names: List<String>, focus: List<Int>): List<SettingsNavEntry> {
    if (names.size != focus.size) return emptyList()
    return names.zip(focus)
        .map { (name, index) -> SettingsSection.entries.find { it.name == name } to index }
        .takeWhile { (section, _) -> section != null && section in RESUMABLE_SECTIONS }
        .map { (section, index) -> SettingsNavEntry(section!!, index.coerceAtLeast(0)) }
}
