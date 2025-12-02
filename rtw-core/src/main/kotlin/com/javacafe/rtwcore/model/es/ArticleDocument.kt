package com.javacafe.rtwcore.model.es

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Elasticsearch에 색인된 문서 모델
 * 
 * 인덱스: rtw-articles
 * rtw-index에서 색인, rtw-serve에서 조회
 */
data class ArticleDocument(
    /**
     * 문서 ID (ES _id로 사용)
     */
    @JsonProperty("refinedId")
    val refinedId: String? = null,
    
    /**
     * 원본 데이터 ID
     */
    @JsonProperty("originId")
    val originId: String? = null,
    
    /**
     * 소스 타입 (RSS, WIKI, TWITTER)
     */
    @JsonProperty("sourceType")
    val sourceType: String? = null,
    
    /**
     * 제목
     */
    @JsonProperty("title")
    val title: String? = null,
    
    /**
     * 본문
     */
    @JsonProperty("fullText")
    val fullText: String? = null,
    
    /**
     * 작성자
     */
    @JsonProperty("author")
    val author: String? = null,
    
    /**
     * 출처
     */
    @JsonProperty("source")
    val source: String? = null,
    
    /**
     * 카테고리
     */
    @JsonProperty("category")
    val category: String? = null,
    
    /**
     * URL
     */
    @JsonProperty("url")
    val url: String? = null,
    
    /**
     * 원본 발행일
     */
    @JsonProperty("publishedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val publishedAt: LocalDateTime? = null,
    
    /**
     * 색인 시각
     */
    @JsonProperty("indexedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val indexedAt: LocalDateTime = LocalDateTime.now(),
    
    /**
     * 추출된 키워드 목록
     */
    @JsonProperty("keywords")
    val keywords: List<String> = emptyList(),
    
    /**
     * 키워드 점수 (nested)
     */
    @JsonProperty("keywordScores")
    val keywordScores: List<KeywordScore> = emptyList(),
    
    /**
     * 텍스트 길이
     */
    @JsonProperty("textLength")
    val textLength: Int = fullText?.length ?: 0,
    
    /**
     * 단어 수
     */
    @JsonProperty("wordCount")
    val wordCount: Int = 0
)

/**
 * 키워드 점수 모델 (nested)
 */
data class KeywordScore(
    @JsonProperty("word")
    val word: String,
    
    @JsonProperty("count")
    val count: Int,
    
    @JsonProperty("score")
    val score: Float
)
