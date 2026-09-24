package com.nendo.argosy.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionPathTest {

    @Test
    fun `a path of re-enterable sections resumes whole with its focus`() {
        val path = resumableSectionPath(
            names = listOf("MAIN", "DISPLAYS", "SCREENS"),
            focus = listOf(9, 2, 1)
        )

        assertEquals(
            listOf(
                SettingsNavEntry(SettingsSection.MAIN, 9),
                SettingsNavEntry(SettingsSection.DISPLAYS, 2),
                SettingsNavEntry(SettingsSection.SCREENS, 1)
            ),
            path
        )
    }

    @Test
    fun `a section that shows one chosen item resumes at its parent`() {
        val path = resumableSectionPath(
            names = listOf("MAIN", "PLATFORMS", "PLATFORM_DETAIL", "CORE_OPTIONS"),
            focus = listOf(4, 7, 0, 3)
        )

        assertEquals(
            listOf(
                SettingsNavEntry(SettingsSection.MAIN, 4),
                SettingsNavEntry(SettingsSection.PLATFORMS, 7)
            ),
            path
        )
    }

    @Test
    fun `an unknown section name ends the path`() {
        val path = resumableSectionPath(
            names = listOf("MAIN", "REMOVED_SECTION", "THEME"),
            focus = listOf(1, 0, 0)
        )

        assertEquals(listOf(SettingsNavEntry(SettingsSection.MAIN, 1)), path)
    }

    @Test
    fun `names and focus of different lengths resume nothing`() {
        assertTrue(resumableSectionPath(listOf("MAIN", "THEME"), listOf(0)).isEmpty())
    }

    @Test
    fun `a negative focus index resumes at the first row`() {
        assertEquals(
            listOf(SettingsNavEntry(SettingsSection.THEME, 0)),
            resumableSectionPath(listOf("THEME"), listOf(-1))
        )
    }

    @Test
    fun `the recorded path is the back stack followed by the section on screen`() {
        val state = SettingsUiState(
            currentSection = SettingsSection.THEME_FONTS,
            focusedIndex = 2,
            backStack = listOf(
                SettingsNavEntry(SettingsSection.MAIN, 5),
                SettingsNavEntry(SettingsSection.THEME, 3)
            )
        )

        assertEquals(
            listOf(
                SettingsNavEntry(SettingsSection.MAIN, 5),
                SettingsNavEntry(SettingsSection.THEME, 3),
                SettingsNavEntry(SettingsSection.THEME_FONTS, 2)
            ),
            state.sectionPath()
        )
    }
}
