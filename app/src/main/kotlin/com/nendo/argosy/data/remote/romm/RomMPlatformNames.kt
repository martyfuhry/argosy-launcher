package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.platform.PlatformDefinitions

/**
 * Display name and short name for a RomM platform, as `name to shortName`.
 *
 * The folder name (`fs_slug`) only names a platform whose slug the registry does not know.
 */
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
