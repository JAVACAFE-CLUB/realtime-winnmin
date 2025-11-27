package com.javacafe.rtwdataclean.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 풀텍스트 추출 결과를 저장하는 MongoDB 도큐먼트
 * 
 * 컬렉션명: full_text_data
 * 
 * 설계 의도:
 * - Kafka에는 ID와 메타데이터만 전송
 * - 실제 풀텍스트 데이터는 MongoDB에 저장하여 메시지 크기 최소화
 * - Elasticsearch 인덱싱 시 MongoDB에서 풀텍스트 조회
 * 
 * 인덱스 전략:
 * 1. refinedId (UNIQUE) - 주요 조회 키
 * 2. originId - 원본 데이터 참조
 * 3. sourceType + createdAt (복합) - 타입별 시계열 조회
 * 4. createdAt (TTL) - 30일 후 자동 삭제
 */
@Document(collection = "full_text_data")
@CompoundIndexes(
    CompoundIndex(
        name = "sourceType_createdAt_idx",
        def = "{'sourceType': 1, 'createdAt': -1}"
    )
)
data class FullTextDocument(
    /**
     * MongoDB ObjectId (자동 생성)
     */
    @Id
    val id: String? = null,
    
    /**
     * 정제된 데이터 ID (Snowflake ID)
     * Kafka 메시지와 매핑되는 고유 식별자
     * UNIQUE 인덱스로 중복 방지
     */
    @Indexed(unique = true)
    val refinedId: String,
    
    /**
     * 원본 데이터 ID (참조용)
     */
    @Indexed
    val originId: String,
    
    /**
     * 데이터 소스 타입 (RSS, WIKI, TWITTER)
     */
    @Indexed
    val sourceType: String,
    
    /**
     * 추출된 풀텍스트 (메인 컨텐츠)
     * 이 필드가 가장 큰 용량을 차지함
     */
    val fullText: String,
    
    /**
     * 원본 데이터의 주요 필드들
     * - RSS: id, title, author, source, pubDate, link, category
     * - WIKI: id, title, author, source, timestamp, contributor
     * - TWITTER: id, title, author, source, createdAt, userId, retweetCount, likeCount
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
     * TTL 인덱스의 기준 필드 (30일 후 자동 삭제)
     */
    @Indexed(expireAfterSeconds = 2592000)  // 30일 = 30 * 24 * 60 * 60
    val createdAt: LocalDateTime = LocalDateTime.now()
)
