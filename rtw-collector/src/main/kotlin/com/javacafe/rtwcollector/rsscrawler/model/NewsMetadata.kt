package com.javacafe.rtwcollector.rsscrawler.model

data class NewsMetadata(
    val fileId: String,
    val sourceName: String,
    val category: String,
    val itemCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)
