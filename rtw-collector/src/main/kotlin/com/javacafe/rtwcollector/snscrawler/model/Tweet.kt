package com.javacafe.rtwcollector.snscrawler.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Tweet(
    val text: String,
    val id: String,
    @JsonProperty("edit_history_tweet_ids")
    val editHistoryTweetIds: List<String>
)
