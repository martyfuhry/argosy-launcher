package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.entity.GameEntity
import javax.inject.Inject

/**
 * The launch intent for a game, with the play session an external emulator launch opens once its
 * start is dispatched. An in-process launch opens its session from LibretroActivity and a resume
 * keeps the running one, so neither prepares a session here and any earlier prepare is dropped.
 */
class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker
) {
    suspend operator fun invoke(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        skipVariantPrompt: Boolean = false,
        allowVariantPrompt: Boolean = true,
        prefetchedGame: GameEntity? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL,
        overrideDisplayId: Int? = null
    ): LaunchResult {
        val result = gameLauncher.launch(
            gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt,
            prefetchedGame, overrideDisplayId
        )
        if (result is LaunchResult.Success && !result.inProcess && !forResume) {
            playSessionTracker.prepareSession(
                gameId = gameId,
                emulatorPackage = result.intent.component?.packageName
                    ?: result.intent.`package`
                    ?: "",
                coreName = extractCoreName(result.intent),
                variantFileId = variantFileId,
                origin = origin
            )
        } else {
            playSessionTracker.discardPreparedSession()
        }
        return result
    }

    private fun extractCoreName(intent: Intent): String? {
        val libretroPath = intent.getStringExtra("LIBRETRO") ?: return null
        val coreFile = libretroPath.substringAfterLast("/")
        return coreFile
            .removeSuffix("_libretro_android.so")
            .removeSuffix("_libretro.so")
            .takeIf { it.isNotEmpty() }
    }
}
