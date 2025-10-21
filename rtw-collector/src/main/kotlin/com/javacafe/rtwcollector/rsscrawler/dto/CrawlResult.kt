package com.javacafe.rtwcollector.rsscrawler.dto

import com.javacafe.rtwcollector.rsscrawler.processor.RssSource

data class CrawlResult(
    val source: RssSource,
    val success: Boolean,
    val itemCount: Int = 0,
    val error: String? = null
)
