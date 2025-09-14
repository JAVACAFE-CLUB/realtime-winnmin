package com.javacafe.rtwcollector.common.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateTimeUtils {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun localDateTimeNowAsStr(): String {
        return LocalDateTime.now().format(FORMATTER)
    }
}
