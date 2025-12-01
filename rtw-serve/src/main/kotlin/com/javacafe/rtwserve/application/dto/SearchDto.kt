package com.javacafe.rtwserve.application.dto

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

/**
 * 검색 요청 DTO
 */
data class SearchRequest(
    val query: String,
    val page: Int = 0,
    val size: Int = 20,
    val sourceType: String? = null,
    val category: String? = null,
    val fromDate: LocalDateTime? = null,
    val toDate: LocalDateTime? = null,
    val sortBy: String = "relevance"  // relevance, date, score
)

/**
 * 검색 응답 DTO
 */
data class SearchResponse(
    val query: String,
    val totalHits: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val articles: List<ArticleDto>,
    val took: Long  // 검색 소요 시간 (ms)
)

/**
 * 문서 DTO
 */
data class ArticleDto(
    val id: String,
    val title: String,
    val content: String,  // 하이라이트된 본문 또는 요약
    val author: String,
    val source: String,
    val sourceType: String,
    val category: String?,
    val url: String?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val publishedAt: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val indexedAt: LocalDateTime?,
    val keywords: List<String>,
    val score: Float?
)

/**
 * 검색 자동완성 응답
 */
data class AutocompleteResponse(
    val query: String,
    val suggestions: List<String>
)
