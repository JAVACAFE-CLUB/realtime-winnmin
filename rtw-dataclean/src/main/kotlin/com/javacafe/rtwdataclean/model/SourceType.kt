package com.javacafe.rtwdataclean.model

/**
 * 데이터 소스 타입별 추출 전략
 */
enum class SourceType(
    val topic: String,
    val textPath: List<String>,  // JSON 경로
    val contentType: ContentType
) {
    RSS(
        topic = "rss-items",
        textPath = listOf("htmlContent"),
        contentType = ContentType.HTML
    ),
    
    WIKI(
        topic = "wiki-items", 
        textPath = listOf("revision", "text"),
        contentType = ContentType.HTML
    ),
    
    TWITTER(
        topic = "twitter-items",
        textPath = listOf("text"),
        contentType = ContentType.PLAIN_TEXT
    );
    
    companion object {
        fun fromTopic(topic: String): SourceType {
            return values().find { it.topic == topic }
                ?: throw IllegalArgumentException("Unknown topic: $topic")
        }
    }
}

enum class ContentType {
    HTML,
    PLAIN_TEXT
}
