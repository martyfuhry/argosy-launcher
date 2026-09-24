package com.nendo.argosy.ui.screens.gamedetail.components

private const val MAX_SECTION_REACH = 30
private const val MIN_RULE_LENGTH = 3
private val RULE_CHARS = setOf('=', '-', '*', '_', '#', '~', '+', '.')

/**
 * The block of text around [lineIndex]: the run of lines bounded by blank lines or divider rules
 * such as `=====`, reaching at most [MAX_SECTION_REACH] lines each way. A blank line resolves to
 * the block that starts below it. Null when the touched line is a divider or nothing follows it.
 */
fun sectionAt(lines: List<String>, lineIndex: Int): IntRange? {
    if (lineIndex !in lines.indices) return null
    val anchor = if (lines[lineIndex].isBlank()) {
        (lineIndex until lines.size).firstOrNull { !lines[it].isBlank() } ?: return null
    } else {
        lineIndex
    }
    if (isBoundary(lines[anchor])) return null
    var first = anchor
    while (first > 0 && anchor - first < MAX_SECTION_REACH && !isBoundary(lines[first - 1])) first--
    var last = anchor
    while (last < lines.lastIndex && last - anchor < MAX_SECTION_REACH && !isBoundary(lines[last + 1])) last++
    return first..last
}

/**
 * [highlights] with [section] added, or with every highlight covering [touchedLine] removed when
 * one already does.
 */
fun toggleHighlight(highlights: List<IntRange>, section: IntRange, touchedLine: Int): List<IntRange> {
    val covering = highlights.filter { touchedLine in it }
    if (covering.isNotEmpty()) return highlights - covering.toSet()
    return (highlights.filterNot { it.first >= section.first && it.last <= section.last } + listOf(section))
        .sortedBy { it.first }
}

private fun isBoundary(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    return trimmed.length >= MIN_RULE_LENGTH && trimmed.all { it in RULE_CHARS || it == ' ' }
}
