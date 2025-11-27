package com.javacafe.rtwdataclean.model

import java.time.LocalDateTime

/**
 * 정재 시 생성되는 메타데이터
 */
data class Metadata(
    val sourceId: String,                   // MongoDB document ID
    val source: String,                     // 수집 소스 (twitter_api, rss_feed, wiki_file)
    val originalChecksum: String,           // 원본 체크섬
    val fileSize: Long,                     // 원본 파일 크기
    val processedAt: LocalDateTime = LocalDateTime.now(),
    val processingVersion: String = "1.0.0",
    val extractionStatus: ExtractionStatus,
    val extractionTimeMs: Long? = null,
    val fullTextLength: Int = 0,
    val errorMessage: String? = null
)

enum class ExtractionStatus {
    SUCCESS,            // 성공
    PARTIAL_SUCCESS,    // 부분 성공 (타임아웃 등)
    FAILED              // 실패
}
