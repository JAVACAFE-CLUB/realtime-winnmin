package com.javacafe.rtwserve.controller

import com.javacafe.rtwserve.application.dto.KeywordArticlesResponse
import com.javacafe.rtwserve.application.dto.KeywordTrendResponse
import com.javacafe.rtwserve.application.dto.TodayKeywordsResponse
import com.javacafe.rtwserve.application.service.KeywordQueryService
import com.javacafe.rtwserve.common.dto.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * 키워드 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/keywords")
class KeywordController(
    private val keywordQueryService: KeywordQueryService
) {
    /**
     * 오늘의 키워드 조회
     * 
     * GET /api/v1/keywords/today
     * GET /api/v1/keywords/today?date=2024-01-15
     */
    @GetMapping("/today")
    fun getTodayKeywords(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): ResponseEntity<ApiResponse<TodayKeywordsResponse>> {
        val targetDate = date ?: LocalDate.now()
        logger.debug { "Get today keywords: date=$targetDate" }
        
        val result = keywordQueryService.getTodayKeywords(targetDate)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    /**
     * 시간별 키워드 조회
     * 
     * GET /api/v1/keywords/hourly
     * GET /api/v1/keywords/hourly?dateTime=2024-01-15T14:00:00
     */
    @GetMapping("/hourly")
    fun getHourlyKeywords(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) dateTime: LocalDateTime?
    ): ResponseEntity<ApiResponse<TodayKeywordsResponse>> {
        val targetDateTime = dateTime ?: LocalDateTime.now()
        logger.debug { "Get hourly keywords: dateTime=$targetDateTime" }
        
        val result = keywordQueryService.getHourlyKeywords(targetDateTime)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    /**
     * 키워드 트렌드 조회
     * 
     * GET /api/v1/keywords/trend?keyword={keyword}&days=7
     */
    @GetMapping("/trend")
    fun getKeywordTrend(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "7") days: Int
    ): ResponseEntity<ApiResponse<KeywordTrendResponse>> {
        logger.debug { "Get keyword trend: keyword=$keyword, days=$days" }
        
        val result = keywordQueryService.getKeywordTrend(keyword, days)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    /**
     * 특정 키워드 관련 문서 조회
     * 
     * GET /api/v1/keywords/{keyword}/articles
     */
    @GetMapping("/{keyword}/articles")
    fun getArticlesByKeyword(
        @PathVariable keyword: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<KeywordArticlesResponse>> {
        logger.debug { "Get articles by keyword: keyword=$keyword, page=$page, size=$size" }
        
        val result = keywordQueryService.getArticlesByKeyword(keyword, page, size)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}
