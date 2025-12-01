package com.javacafe.rtwcore.model.mongodb

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * MongoDB full_text_data 컬렉션 문서
 * 
 * rtw-dataclean에서 저장한 풀텍스트 데이터
 * rtw-index, rtw-serve에서 조회
 */
@Document(collection = "full_text_data")
data class FullTextDocument(
    @Id
    val id: String? = null,
    
    /**
     * 정제된 데이터 ID (Snowflake ID)
     */
    val refinedId: String,
    
    /**
     * 원본 데이터 ID
     */
    val originId: String,
    
    /**
     * 데이터 소스 타입 (RSS, WIKI, TWITTER)
     */
    val sourceType: String,
    
    /**
     * 추출된 풀텍스트 (메인 컨텐츠)
     */
    val fullText: String,
    
    /**
     * 원본 데이터의 주요 필드들
     */
    val originalData: Map<String, Any>,
    
    /**
     * 추출 성공 여부
     */
    val extractionSuccess: Boolean,
    
    /**
     * 추출 소요 시간 (밀리초)
     */
    val extractionTimeMs: Long,
    
    /**
     * 풀텍스트 길이
     */
    val fullTextLength: Int,
    
    /**
     * 에러 메시지 (실패 시)
     */
    val errorMessage: String? = null,
    
    /**
     * 생성 시각
     */
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 원본 데이터에서 title 추출
     */
    fun getTitle(): String {
        return originalData["title"]?.toString() ?: ""
    }
    
    /**
     * 원본 데이터에서 author 추출
     */
    fun getAuthor(): String {
        return originalData["author"]?.toString() ?: ""
    }
    
    /**
     * 원본 데이터에서 source 추출
     */
    fun getSource(): String {
        return originalData["source"]?.toString() ?: ""
    }
    
    /**
     * 원본 데이터에서 category 추출
     */
    fun getCategory(): String {
        return originalData["category"]?.toString() ?: ""
    }
    
    /**
     * 원본 데이터에서 URL 추출
     */
    fun getUrl(): String {
        return originalData["link"]?.toString() 
            ?: originalData["url"]?.toString() 
            ?: ""
    }
    
    /**
     * 원본 데이터에서 발행일 추출
     */
    fun getPublishedAt(): String? {
        return originalData["pubDate"]?.toString()
            ?: originalData["timestamp"]?.toString()
            ?: originalData["createdAt"]?.toString()
    }
}
