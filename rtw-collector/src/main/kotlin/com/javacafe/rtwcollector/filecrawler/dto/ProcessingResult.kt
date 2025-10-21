package com.javacafe.rtwcollector.filecrawler.dto

data class ProcessingResult(
    val totalPages: Int,
    val totalChunks: Int,
    val errors: List<String>
) {
    val isSuccessful: Boolean = errors.isEmpty()
    val hasPartialSuccess: Boolean = totalChunks > 0 && errors.isNotEmpty()

    fun summary(): String = buildString {
        append("Processing completed: ")
        append("$totalPages pages in $totalChunks chunks")
        if (errors.isNotEmpty()) {
            append(", ${errors.size} errors")
        }
    }
}
