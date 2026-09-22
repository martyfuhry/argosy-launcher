package com.nendo.argosy.util

import java.io.File

/**
 * Whether this file resolves inside [dir], with the directory itself counting as inside. A
 * sibling whose canonical path merely starts with the directory's is outside.
 */
fun File.isInside(dir: File): Boolean {
    val root = runCatching { dir.canonicalPath }.getOrNull() ?: return false
    val candidate = runCatching { canonicalPath }.getOrNull() ?: return false
    return candidate == root || candidate.startsWith(root + File.separator)
}
