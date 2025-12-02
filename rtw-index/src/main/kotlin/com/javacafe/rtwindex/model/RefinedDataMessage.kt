package com.javacafe.rtwindex.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * rtw-dataclean에서 수신하는 Kafka 메시지
 * 
 * 정제 시스템에서 발행한 경량 메시지
 * - refinedId: MongoDB full_text_data 컬렉션 조회 키
 * - metadata: 처리 정보
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RefinedDataMessage(
    @JsonProperty("refined_id")
    val refinedId: String,
    
    @JsonProperty("metadata")
    val metadata: MessageMetadata
)

/**
 * 메시지 메타데이터
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MessageMetadata(
    val sourceId: String,
    val source: String,
    val originalChecksum: String,
    val fileSize: Long,
    val processedAt: LocalDateTime,
    val processingVersion: String = "1.0.0",
    val extractionStatus: String,
    val extractionTimeMs: Long? = null,
    val fullTextLength: Int = 0,
    val errorMessage: String? = null
)

/**
 * 소스 타입 enum
 */
enum class SourceType(val topicName: String, val displayName: String) {
    RSS("refined-rss", "RSS"),
    WIKI("refined-wiki", "Wikipedia"),
    TWITTER("refined-twitter", "Twitter");
    
    companion object {
        fun fromTopic(topic: String): SourceType? {
            return entries.find { it.topicName == topic }
        }
    }
}
