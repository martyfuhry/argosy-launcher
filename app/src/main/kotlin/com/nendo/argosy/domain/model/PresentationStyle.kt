package com.nendo.argosy.domain.model

import org.json.JSONArray
import org.json.JSONObject

enum class PresentationScrim { GRADIENT, SOLID, BLUR, NONE }

enum class PresentationArt { COVER, BOX_3D, TITLE }

/**
 * The facts the presentation screen can show about a focused game, in display order.
 */
enum class PresentationStat {
    DEVELOPER,
    RELEASE_YEAR,
    GENRE,
    COMMUNITY_RATING,
    USER_RATING,
    PLAY_TIME,
    TIME_TO_BEAT,
    ACHIEVEMENTS,
    FRIENDS
}

const val PRESENTATION_SCRIM_MIN = 0
const val PRESENTATION_SCRIM_MAX = 100
const val PRESENTATION_SCRIM_STEP = 10

/**
 * How the presentation screen draws a focused game. The defaults reproduce the view that shipped
 * before the style was configurable.
 *
 * @param scrimStrength how opaque the scrim is at its darkest, in percent.
 */
data class PresentationStyle(
    val scrim: PresentationScrim = PresentationScrim.GRADIENT,
    val scrimStrength: Int = DEFAULT_SCRIM_STRENGTH,
    val art: PresentationArt = PresentationArt.COVER,
    val stats: Set<PresentationStat> = DEFAULT_STATS
) {
    fun shows(stat: PresentationStat): Boolean = stat in stats

    fun toJson(): String = JSONObject().apply {
        put(KEY_SCRIM, scrim.name)
        put(KEY_SCRIM_STRENGTH, scrimStrength)
        put(KEY_ART, art.name)
        put(KEY_STATS, JSONArray(stats.map { it.name }))
    }.toString()

    companion object {
        const val DEFAULT_SCRIM_STRENGTH = 90
        val DEFAULT_STATS: Set<PresentationStat> = setOf(
            PresentationStat.DEVELOPER,
            PresentationStat.RELEASE_YEAR,
            PresentationStat.GENRE,
            PresentationStat.FRIENDS
        )

        private const val KEY_SCRIM = "scrim"
        private const val KEY_SCRIM_STRENGTH = "scrimStrength"
        private const val KEY_ART = "art"
        private const val KEY_STATS = "stats"

        /**
         * Reads each field independently and defaults the rest, dropping stat names this build does
         * not know.
         */
        fun fromJson(raw: String?): PresentationStyle {
            if (raw.isNullOrBlank()) return PresentationStyle()
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return PresentationStyle()
            val defaults = PresentationStyle()
            val stats = root.optJSONArray(KEY_STATS)?.let { array ->
                (0 until array.length())
                    .mapNotNull { index -> enumOrNull<PresentationStat>(array.optString(index)) }
                    .toSet()
            }
            return PresentationStyle(
                scrim = enumOrNull<PresentationScrim>(root.optString(KEY_SCRIM)) ?: defaults.scrim,
                scrimStrength = root.optInt(KEY_SCRIM_STRENGTH, defaults.scrimStrength)
                    .coerceIn(PRESENTATION_SCRIM_MIN, PRESENTATION_SCRIM_MAX),
                art = enumOrNull<PresentationArt>(root.optString(KEY_ART)) ?: defaults.art,
                stats = stats ?: defaults.stats
            )
        }

        private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
            raw?.takeIf { it.isNotBlank() }?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
    }
}
