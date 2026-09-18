package com.nendo.argosy.data.download

object ServerRomLayout {

    fun relativeDir(filePath: String, siblingPaths: List<String>): String? {
        if (siblingPaths.isEmpty()) return null
        val root = siblingPaths
            .map { segmentsOf(it) }
            .reduce { common, segments ->
                common.zip(segments).takeWhile { it.first == it.second }.map { it.first }
            }
        return segmentsOf(filePath)
            .drop(root.size)
            .joinToString("/")
            .takeIf { it.isNotEmpty() }
    }

    private fun segmentsOf(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() }
}
