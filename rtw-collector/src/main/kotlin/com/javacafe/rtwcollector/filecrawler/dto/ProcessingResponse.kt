package com.javacafe.rtwcollector.filecrawler.dto

import com.javacafe.rtwcollector.common.utils.DateTimeUtils

data class ProcessingResponse(
    val success: Boolean,
    val message: String,
    val totalPages: Int = 0,
    val totalChunks: Int = 0,
    val errors: List<String> = emptyList(),
    val summary: String = "",
    val timestamp: String = DateTimeUtils.localDateTimeNowAsStr()
) {
    companion object {
        fun error(message: String) = ProcessingResponse(
            success = false,
            message = message
        )
    }
}
