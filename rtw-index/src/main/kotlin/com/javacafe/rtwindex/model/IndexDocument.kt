package com.javacafe.rtwindex.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.javacafe.rtwcore.model.mongodb.FullTextDocument
import java.time.LocalDateTime

/**
 * Elasticsearch에 색인할 문서 모델
 * 
 * 인덱스: rtw-articles
 * 색인 전용 모델 (rtw-index에서만 사용)
 */
data class IndexDocument(
    /**
     * 문서 ID (ES _id로 사용)
     */
    @JsonProperty("refinedId")
    val refinedId: String,
    
    /**
     * 원본 데이터 ID
     */
    @JsonProperty("originId")
    val originId: String,
    
    /**
     * 소스 타입 (RSS, WIKI, TWITTER)
     */
    @JsonProperty("sourceType")
    val sourceType: String,
    
    /**
     * 제목
     */
    @JsonProperty("title")
    val title: String,
    
    /**
     * 본문 (형태소 분석 대상)
     */
    @JsonProperty("fullText")
    val fullText: String,
    
    /**
     * 작성자
     */
    @JsonProperty("author")
    val author: String,
    
    /**
     * 출처
     */
    @JsonProperty("source")
    val source: String,
    
    /**
     * 카테고리
     */
    @JsonProperty("category")
    val category: String?,
    
    /**
     * URL
     */
    @JsonProperty("url")
    val url: String?,
    
    /**
     * 원본 발행일
     */
    @JsonProperty("publishedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val publishedAt: LocalDateTime?,
    
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
    val textLength: Int = fullText.length,
    
    /**
     * 단어 수 (공백 기준)
     */
    @JsonProperty("wordCount")
    val wordCount: Int = fullText.split("\\s+".toRegex()).size
) {
    companion object {
        /**
         * FullTextDocument로부터 IndexDocument 생성
         */
        fun from(doc: FullTextDocument): IndexDocument {
            return IndexDocument(
                refinedId = doc.refinedId,
                originId = doc.originId,
                sourceType = doc.sourceType,
                title = doc.getTitle(),
                fullText = doc.fullText,
                author = doc.getAuthor(),
                source = doc.getSource(),
                category = doc.getCategory().ifEmpty { null },
                url = doc.getUrl().ifEmpty { null },
                publishedAt = parseDateTime(doc.getPublishedAt()),
                textLength = doc.fullTextLength,
                wordCount = doc.fullText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            )
        }
        
        private fun parseDateTime(dateStr: String?): LocalDateTime? {
            if (dateStr.isNullOrBlank()) return null
            
            return try {
                LocalDateTime.parse(dateStr)
            } catch (e: Exception) {
                try {
                    java.time.ZonedDateTime.parse(dateStr).toLocalDateTime()
                } catch (e2: Exception) {
                    null
                }
            }
        }
    }
}

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
