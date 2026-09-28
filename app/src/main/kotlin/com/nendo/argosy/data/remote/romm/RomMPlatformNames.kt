package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.platform.PlatformDefinitions

internal fun RomMPlatform.resolvePlatformNames(effectiveSlug: String): Pair<String, String> {
    val platformDef = PlatformDefinitions.getBySlug(effectiveSlug)
    val isSubPlatform = !effectiveSlug.equals(slug, ignoreCase = true)
    val derivedNames = if (isSubPlatform) {
        PlatformDefinitions.getAliasDisplayName(effectiveSlug)
            ?: PlatformDefinitions.deriveDisplayName(effectiveSlug)
    } else {
        PlatformDefinitions.getAliasDisplayName(slug)
            ?: PlatformDefinitions.deriveDisplayName(slug)
            ?: fsSlug.takeIf { platformDef == null }?.let(PlatformDefinitions::deriveDisplayName)
    }
    val normalizedName = if (isSubPlatform) {
        customName?.takeIf { it.isNotBlank() }
            ?: derivedNames?.first ?: platformDef?.name ?: name
    } else {
        customName?.takeIf { it.isNotBlank() }
            ?: displayName ?: derivedNames?.first ?: name
    }
    val resolvedShortName = derivedNames?.second ?: platformDef?.shortName ?: normalizedName
    return normalizedName to resolvedShortName
}
