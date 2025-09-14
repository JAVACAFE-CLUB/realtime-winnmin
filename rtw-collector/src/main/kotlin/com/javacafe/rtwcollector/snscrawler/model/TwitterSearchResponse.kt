package com.javacafe.rtwcollector.snscrawler.model

data class TwitterSearchResponse(
    val data: List<Tweet>?,
    val meta: Meta?
)
