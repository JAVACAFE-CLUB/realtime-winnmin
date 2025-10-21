package com.javacafe.rtwcollector.rsscrawler.dto

import com.javacafe.rtwcore.utils.DateTimeUtils


data class NewsCrawlResponse(
    val status: CrawlStatus,
    val message: String,
    val totalSources: Int = 0,
    val successfulSources: Int = 0,
    val failedSources: Int = 0,
    val errors: List<String> = emptyList(),
    val timestamp: String = DateTimeUtils.localDateTimeNowAsStr()
) {
    companion object {
        fun success(totalSources: Int, successfulSources: Int, failedSources: Int) =
            NewsCrawlResponse(
                status = CrawlStatus.SUCCESS,
                message = "News crawling completed successfully",
                totalSources = totalSources,
                successfulSources = successfulSources,
                failedSources = failedSources
            )

        fun partialSuccess(totalSources: Int, successfulSources: Int, failedSources: Int, errors: List<String>) =
            NewsCrawlResponse(
                status = CrawlStatus.PARTIAL_SUCCESS,
                message = "News crawling completed with some errors",
                totalSources = totalSources,
                successfulSources = successfulSources,
                failedSources = failedSources,
                errors = errors
            )

        fun error(message: String, errors: List<String> = emptyList()) =
            NewsCrawlResponse(
                status = CrawlStatus.ERROR,
                message = message,
                errors = errors
            )
    }
}

enum class CrawlStatus {
    SUCCESS, PARTIAL_SUCCESS, ERROR
}
