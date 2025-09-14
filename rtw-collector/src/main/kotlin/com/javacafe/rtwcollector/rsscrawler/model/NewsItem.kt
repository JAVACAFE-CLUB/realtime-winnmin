package com.javacafe.rtwcollector.rsscrawler.model

data class NewsItem(
    val title: String,
    val link: String,
    val pubDate: String,
    val author: String,
    val category: String,
    val htmlContent: String
)
