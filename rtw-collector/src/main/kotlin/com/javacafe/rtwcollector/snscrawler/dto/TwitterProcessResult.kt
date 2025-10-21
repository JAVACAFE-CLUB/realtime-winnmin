package com.javacafe.rtwcollector.snscrawler.dto

data class TwitterProcessResult(
    val fileId: String,
    val fileSize: Long,
    val actualCount: Int,
    val query: String,
    val maxResults: Int
)
