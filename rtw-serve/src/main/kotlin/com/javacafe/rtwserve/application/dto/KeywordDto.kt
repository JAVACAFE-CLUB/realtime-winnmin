package com.javacafe.rtwserve.application.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.javacafe.rtwcore.model.es.HourlyCount
import com.javacafe.rtwcore.model.es.SourceBreakdown
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 키워드 응답 DTO
 */
data class KeywordDto(
    val keyword: String,
    val rank: Int,
    val frequency: Long,
    val documentCount: Int,
    val score: Float,
    val sourceBreakdown: SourceBreakdown?,
    val hourlyTrend: List<HourlyCount>?,
    val previousRank: Int?,
    val rankChange: Int?,
    val isNew: Boolean
)

/**
 * 오늘의 키워드 응답
 */
data class TodayKeywordsResponse(
    @JsonFormat(pattern = "yyyy-MM-dd")
    val date: LocalDate,
    val keywords: List<KeywordDto>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val generatedAt: LocalDateTime,
    val cached: Boolean = true
)

/**
 * 키워드 트렌드 응답
 */
data class KeywordTrendResponse(
    val keyword: String,
    val trend: List<DailyFrequency>
)

/**
 * 일별 빈도
 */
data class DailyFrequency(
    @JsonFormat(pattern = "yyyy-MM-dd")
    val date: LocalDate,
    val frequency: Long,
    val rank: Int?
)

/**
 * 키워드 관련 문서 응답
 */
data class KeywordArticlesResponse(
    val keyword: String,
    val totalHits: Long,
    val articles: List<ArticleDto>
)
