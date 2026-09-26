package com.nendo.argosy.ui.screens.common

import io.mockk.mockkClass

/**
 * An instance of [T] built through its widest constructor, taking each parameter from
 * [overrides] when one is an instance of that parameter's type and a relaxed mock otherwise.
 */
internal inline fun <reified T : Any> relaxedInstance(vararg overrides: Any): T {
    val constructor = T::class.java.constructors.maxBy { it.parameterCount }
    val arguments = constructor.parameterTypes.map { type ->
        overrides.firstOrNull { type.isInstance(it) } ?: mockkClass(type.kotlin, relaxed = true)
    }
    return constructor.newInstance(*arguments.toTypedArray()) as T
}
