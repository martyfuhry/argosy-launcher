package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.ApkAssetMatcher

data class InstallerVariantUi(
    @StringRes val labelRes: Int?,
    val label: String?,
    val assetName: String
)

fun installerVariantUi(assetName: String): InstallerVariantUi {
    val variant = ApkAssetMatcher.extractVariantFromAssetName(assetName)
    val labelRes = when (variant) {
        "universal", "all", "fat", "multi" -> R.string.settings_installers_variant_universal
        null -> R.string.settings_installers_variant_default
        else -> null
    }
    return InstallerVariantUi(
        labelRes = labelRes,
        label = if (labelRes == null) ApkAssetMatcher.formatVariantDisplay(variant) else null,
        assetName = assetName
    )
}
