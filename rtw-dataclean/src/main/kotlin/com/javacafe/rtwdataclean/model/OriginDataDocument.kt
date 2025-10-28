package com.javacafe.rtwdataclean.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * MongoDB origin_data 컬렉션의 Document 모델
 * rtw-collector에서 생성한 데이터를 읽기 위한 모델
 */
@Document(collection = "origin_data")
data class OriginDataDocument(
    @Id
    val id: String,
    val prefix: String,
    val source: String,
    val data: Any,
    val dataSize: Long,
    val checksum: String,
    val createdAt: LocalDateTime,
    val metadata: Map<String, Any> = emptyMap(),
    val updatedAt: LocalDateTime? = null
)
