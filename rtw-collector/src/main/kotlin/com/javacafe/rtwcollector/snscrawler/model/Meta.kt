package com.javacafe.rtwcollector.snscrawler.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Meta(
    @JsonProperty("newest_id")
    val newestId: String,
    @JsonProperty("oldest_id")
    val oldestId: String,
    @JsonProperty("result_count")
    val resultCount: Int,
    @JsonProperty("next_token")
    val nextToken: String?
)
