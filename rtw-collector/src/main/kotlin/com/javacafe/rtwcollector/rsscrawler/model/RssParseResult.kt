package com.javacafe.rtwcollector.rsscrawler.model

import com.javacafe.rtwcollector.rsscrawler.processor.RssSource

data class RssParseResult(
    val source: RssSource,
    val items: List<NewsItem>
)
