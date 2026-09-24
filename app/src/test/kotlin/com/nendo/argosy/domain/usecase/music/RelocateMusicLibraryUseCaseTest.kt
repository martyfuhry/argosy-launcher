package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RelocateMusicLibraryUseCaseTest {

    private val musicDirectoryManager = mockk<MusicDirectoryManager>(relaxed = true) {
        every { defaultMusicDir() } returns File("/storage/emulated/0/Music/RomM")
    }
    private val storagePreferences = mockk<StoragePreferencesRepository>(relaxed = true)

    private val useCase = RelocateMusicLibraryUseCase(
        musicDirectoryManager = musicDirectoryManager,
        bgmPlaylistRepository = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        controlsPreferencesRepository = mockk(relaxed = true),
        storagePreferences = storagePreferences,
        attributionRepository = mockk(relaxed = true)
    )

    @Test
    fun `choosing another folder stores it as the override`() = runTest {
        useCase("/storage/emulated/0/Music/RomM", "/storage/75D7-DC5F/Music", moveFiles = false)

        coVerify { storagePreferences.setMusicStoragePath("/storage/75D7-DC5F/Music") }
    }

    @Test
    fun `returning to the default folder clears the override`() = runTest {
        useCase("/storage/75D7-DC5F/Music", "/storage/emulated/0/Music/RomM", moveFiles = false)

        coVerify { storagePreferences.setMusicStoragePath(null) }
    }
}
