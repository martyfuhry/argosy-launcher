package com.nendo.argosy.data.installer

import com.nendo.argosy.data.local.entity.ManagedInstallerEntity

const val CONTROLLER_KEYBOARD_PACKAGE = "com.handheldkeyboard.ime"

object SeededInstallers {

    val ALL: List<ManagedInstallerEntity> = listOf(
        ManagedInstallerEntity(
            repoOwner = "luisho24",
            repoName = "HandheldKeyboard",
            displayName = "Handheld Keyboard",
            packageName = CONTROLLER_KEYBOARD_PACKAGE,
            locked = true,
            sortOrder = 0
        )
    )

    fun isControllerKeyboard(entity: ManagedInstallerEntity): Boolean =
        entity.packageName == CONTROLLER_KEYBOARD_PACKAGE
}
