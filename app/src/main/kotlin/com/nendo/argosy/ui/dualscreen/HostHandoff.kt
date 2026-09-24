package com.nendo.argosy.ui.dualscreen

/**
 * State the launcher host giving up PRIMARY passes to the host taking it over. The hosting
 * launcher attaches a source, a role change captures it, and only a host composed under the
 * arrangement it was captured for can claim it, once.
 */
class HostHandoff<T : Any> {

    private var source: (() -> T?)? = null
    private var pending: Pair<Boolean, T>? = null

    fun attach(source: () -> T?) {
        this.source = source
    }

    fun detach(source: () -> T?) {
        if (this.source === source) this.source = null
    }

    fun capture(forSwapped: Boolean) {
        pending = source?.invoke()?.let { forSwapped to it }
    }

    fun claim(currentSwapped: Boolean): T? {
        val (target, value) = pending ?: return null
        pending = null
        return value.takeIf { target == currentSwapped }
    }
}
