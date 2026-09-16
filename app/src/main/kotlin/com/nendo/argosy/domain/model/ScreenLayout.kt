package com.nendo.argosy.domain.model

import org.json.JSONObject

enum class ScreenRole {
    PRIMARY,
    PRESENTATION,
    APP_TARGET,
    OFF;

    companion object {
        fun fromString(value: String?): ScreenRole = entries.find { it.name == value } ?: PRESENTATION
    }
}

data class ScreenLayout(val roles: Map<String, ScreenRole> = emptyMap()) {

    fun roleFor(screenKey: String): ScreenRole? = roles[screenKey]

    fun withRole(screenKey: String, role: ScreenRole): ScreenLayout {
        val previous = roles[screenKey] ?: ScreenRole.PRESENTATION
        val holder = roles.entries.find { it.value == role && it.key != screenKey }?.key
        val next = roles.toMutableMap()
        next[screenKey] = role
        if (holder != null && role != ScreenRole.OFF) {
            next[holder] = when {
                role == ScreenRole.PRIMARY && previous == ScreenRole.PRESENTATION -> ScreenRole.PRESENTATION
                role == ScreenRole.PRESENTATION && previous == ScreenRole.PRIMARY -> ScreenRole.PRIMARY
                role == ScreenRole.PRIMARY -> ScreenRole.PRESENTATION
                next.none { it.value == ScreenRole.PRESENTATION } -> ScreenRole.PRESENTATION
                else -> ScreenRole.OFF
            }
        }
        return ScreenLayout(next)
    }

    val primaryKey: String? get() = roles.entries.find { it.value == ScreenRole.PRIMARY }?.key

    val presentationKey: String? get() = roles.entries.find { it.value == ScreenRole.PRESENTATION }?.key

    val appTargetKey: String? get() = roles.entries.find { it.value == ScreenRole.APP_TARGET }?.key

    val isSingleDisplay: Boolean get() = presentationKey == null

    fun toJson(): String = JSONObject().apply {
        roles.forEach { (key, role) -> put(key, role.name) }
    }.toString()

    companion object {
        fun fromJson(json: String?): ScreenLayout {
            if (json.isNullOrBlank()) return ScreenLayout()
            return try {
                val obj = JSONObject(json)
                val roles = obj.keys().asSequence().associateWith { key ->
                    ScreenRole.fromString(obj.optString(key))
                }
                ScreenLayout(roles)
            } catch (_: Exception) {
                ScreenLayout()
            }
        }

        fun defaultFor(screenKeys: List<String>, builtInKeys: List<String>): ScreenLayout {
            val primary = builtInKeys.lastOrNull() ?: screenKeys.firstOrNull() ?: return ScreenLayout()
            return ScreenLayout(
                screenKeys.associateWith { key ->
                    if (key == primary) ScreenRole.PRIMARY else ScreenRole.PRESENTATION
                }
            )
        }
    }
}

data class ScreenLayouts(val bySet: Map<String, ScreenLayout> = emptyMap()) {

    fun layoutFor(setKey: String): ScreenLayout? = bySet[setKey]

    fun with(setKey: String, layout: ScreenLayout): ScreenLayouts =
        ScreenLayouts(bySet + (setKey to layout))

    fun toJson(): String = JSONObject().apply {
        bySet.forEach { (setKey, layout) -> put(setKey, JSONObject(layout.toJson())) }
    }.toString()

    companion object {
        fun fromJson(json: String?): ScreenLayouts {
            if (json.isNullOrBlank()) return ScreenLayouts()
            return try {
                val obj = JSONObject(json)
                val sets = obj.keys().asSequence().associateWith { setKey ->
                    ScreenLayout.fromJson(obj.optJSONObject(setKey)?.toString())
                }
                ScreenLayouts(sets)
            } catch (_: Exception) {
                ScreenLayouts()
            }
        }

        fun setKeyOf(screenKeys: List<String>): String = screenKeys.sorted().joinToString("|")
    }
}
