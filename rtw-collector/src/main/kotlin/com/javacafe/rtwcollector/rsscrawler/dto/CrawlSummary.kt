package com.javacafe.rtwcollector.rsscrawler.dto

data class CrawlSummary(
    val totalSources: Int,
    val results: List<CrawlResult>
) {
    val successfulSources = results.count { it.success }
    val failedSources = results.count { !it.success }
    val errors = results.mapNotNull { it.error }
    val totalItems = results.sumOf { it.itemCount }

    val isFullySuccessful = failedSources == 0
    val hasPartialSuccess = successfulSources > 0 && failedSources > 0
}
