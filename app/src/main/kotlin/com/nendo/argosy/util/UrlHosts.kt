package com.nendo.argosy.util

import java.net.URI

/**
 * Whether [url] and [baseUrl] address the same host and port, the port defaulting to the one
 * the scheme implies. A value that does not parse, names no host, or carries a scheme without
 * a known default port never matches.
 */
fun isSameHost(url: String, baseUrl: String): Boolean {
    val target = authorityOf(url) ?: return false
    val base = authorityOf(baseUrl) ?: return false
    return target == base
}

private fun authorityOf(value: String): Pair<String, Int>? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.ifEmpty { null } ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val port = uri.port.takeIf { it != -1 } ?: defaultPortFor(scheme) ?: return null
    return host to port
}

private fun defaultPortFor(scheme: String): Int? = when (scheme) {
    "http" -> 80
    "https" -> 443
    else -> null
}
