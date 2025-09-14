package com.javacafe.rtwcollector.snscrawler.dto

import com.javacafe.rtwcollector.common.utils.DateTimeUtils

data class TwitterCrawlResponse(
    val success: Boolean,
    val message: String,
    val query: String,
    val maxResults: Int,
    val actualCount: Int = 0,
    val fileId: String? = null,
    val fileSize: Long = 0,
    val error: String? = null,
    val timestamp: String = DateTimeUtils.localDateTimeNowAsStr()
) {
    companion object {
        fun success(
            query: String,
            maxResults: Int,
            actualCount: Int,
            fileId: String,
            fileSize: Long
        ) = TwitterCrawlResponse(
            success = true,
            message = "Twitter search completed successfully",
            query = query,
            maxResults = maxResults,
            actualCount = actualCount,
            fileId = fileId,
            fileSize = fileSize
        )

        fun error(
            query: String,
            maxResults: Int,
            errorMessage: String
        ) = TwitterCrawlResponse(
            success = false,
            message = "Twitter search failed",
            query = query,
            maxResults = maxResults,
            error = errorMessage
        )
    }
}
