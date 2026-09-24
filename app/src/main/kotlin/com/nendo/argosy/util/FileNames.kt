package com.nendo.argosy.util

/**
 * Folds a name supplied by a server or an archive into one a volume will accept.
 *
 * Android refuses these characters on emulated and FAT-family storage and fails the create with
 * EPERM rather than substituting anything, so a name has to be folded before it reaches the
 * filesystem. The set mirrors `android.os.FileUtils.buildValidFatFilename`.
 *
 * Refused characters are dropped rather than replaced, so "Legends: Z-A" keeps reading as
 * "Legends Z-A" instead of carrying a placeholder. [normalizeForMatch] exists because that makes
 * the on-disk name differ from the one the server knows: matching has to fold both sides the same
 * way, and it treats an underscore as a space so files a user placed with the older convention
 * still answer to their server name.
 */
object FileNames {

    val INVALID_CHARS = Regex("[\\\\:*?\"<>|/\\x00-\\x1f]")

    private val WHITESPACE_RUN = Regex("\\s+")

    private const val FALLBACK = "file"

    /**
     * A single name as it can exist on disk. Path separators go the way of any other refused
     * character, so the result never reaches outside its directory.
     */
    fun sanitize(name: String): String {
        val folded = INVALID_CHARS.replace(name, "")
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .trimEnd('.', ' ')
        return folded.ifBlank { FALLBACK }
    }

    /**
     * [name] as a directory entry: the fallback stands in for a blank name and for the two
     * entries every directory already holds.
     */
    fun entryName(name: String): String = if (isEntryName(name)) name else FALLBACK

    /**
     * Whether [name] can stand as a directory entry of its own, being neither blank nor `.`
     * nor `..`.
     */
    fun isEntryName(name: String): Boolean = name.isNotBlank() && name != "." && name != ".."

    /**
     * A platform slug as it can name a folder. A blank slug stays blank, since it is how a
     * platform without one is recognised and it already resolves to the parent folder.
     */
    fun sanitizeSlug(slug: String): String = if (slug.isBlank()) slug else sanitize(slug)

    /**
     * An archive entry path with every segment folded. Empty, `.` and `..` segments are dropped,
     * so a crafted entry cannot climb out of the directory it is being extracted into.
     */
    fun sanitizeRelativePath(path: String): String =
        path.split('/', '\\')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/") { sanitize(it) }

    /**
     * The comparable form of a name, for deciding whether a file on disk is the one a server
     * reported. Folds what [sanitize] drops, reads an underscore as the space it stood in for,
     * and ignores case and spacing.
     */
    fun normalizeForMatch(name: String): String =
        INVALID_CHARS.replace(name, "")
            .replace('_', ' ')
            .replace(WHITESPACE_RUN, " ")
            .lowercase()
            .trim()
}
