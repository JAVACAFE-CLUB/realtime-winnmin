package com.javacafe.rtwindex.controller

import com.javacafe.rtwcore.model.es.TopKeywordsResponse
import com.javacafe.rtwindex.scheduler.KeywordScheduler
import com.javacafe.rtwindex.service.KeywordCacheService
import com.javacafe.rtwindex.service.MorphemeAnalyzerService
import com.javacafe.rtwindex.service.TokenFrequency
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

/**
 * 키워드 운영 API 컨트롤러
 * 
 * 색인 시스템 운영/관리용 API
 * 조회 API는 rtw-serve에서 제공
 */
@RestController
@RequestMapping("/api/admin/keywords")
class KeywordController(
    private val keywordCacheService: KeywordCacheService,
    private val morphemeAnalyzerService: MorphemeAnalyzerService,
    private val keywordScheduler: KeywordScheduler
) {

    /**
     * 키워드 캐시 수동 갱신
     * 
     * POST /api/admin/keywords/refresh
     */
    @PostMapping("/refresh")
    fun refreshKeywords(
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        date: LocalDate?
    ): ResponseEntity<TopKeywordsResponse> {
        val targetDate = date ?: LocalDate.now()
        logger.info { "POST /api/admin/keywords/refresh?date=$targetDate" }
        val response = keywordCacheService.refreshDailyKeywords(targetDate)
        return ResponseEntity.ok(response)
    }

    /**
     * 텍스트 형태소 분석 (테스트용)
     * 
     * POST /api/admin/keywords/analyze
     */
    @PostMapping("/analyze")
    fun analyzeText(
        @RequestBody request: AnalyzeRequest
    ): ResponseEntity<AnalyzeResponse> {
        logger.info { "POST /api/admin/keywords/analyze (text length: ${request.text.length})" }
        
        val tokens = morphemeAnalyzerService.analyze(
            text = request.text,
            analyzer = request.analyzer ?: "korean_noun_only"
        )
        
        val topTokens = morphemeAnalyzerService.getTopTokens(
            text = request.text,
            topN = request.topN ?: 20,
            analyzer = request.analyzer ?: "korean_noun_only"
        )
        
        return ResponseEntity.ok(
            AnalyzeResponse(
                totalTokens = tokens.size,
                tokens = tokens.map { it.token },
                topKeywords = topTokens
            )
        )
    }

    /**
     * 캐시 상태 조회
     * 
     * GET /api/admin/keywords/cache/status
     */
    @GetMapping("/cache/status")
    fun getCacheStatus(): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /api/admin/keywords/cache/status" }
        return ResponseEntity.ok(keywordCacheService.getCacheStatus())
    }

    /**
     * 캐시 삭제
     * 
     * DELETE /api/admin/keywords/cache
     */
    @DeleteMapping("/cache")
    fun clearCache(
        @RequestParam(required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) 
        date: LocalDate?
    ): ResponseEntity<Map<String, String>> {
        if (date != null) {
            keywordCacheService.evictCache(date)
            logger.info { "Cleared cache for date: $date" }
            return ResponseEntity.ok(mapOf("message" to "Cache cleared for $date"))
        } else {
            keywordCacheService.evictAllCaches()
            logger.info { "Cleared all keyword caches" }
            return ResponseEntity.ok(mapOf("message" to "All caches cleared"))
        }
    }

    /**
     * 스케줄러 상태 조회
     * 
     * GET /api/admin/keywords/scheduler/status
     */
    @GetMapping("/scheduler/status")
    fun getSchedulerStatus(): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /api/admin/keywords/scheduler/status" }
        return ResponseEntity.ok(keywordScheduler.getSchedulerStatus())
    }

    /**
     * 분석기 정보 조회
     * 
     * GET /api/admin/keywords/analyzer/info
     */
    @GetMapping("/analyzer/info")
    fun getAnalyzerInfo(): ResponseEntity<Map<String, Any>> {
        logger.info { "GET /api/admin/keywords/analyzer/info" }
        return ResponseEntity.ok(morphemeAnalyzerService.getAnalyzerInfo())
    }
}

/**
 * 분석 요청
 */
data class AnalyzeRequest(
    val text: String,
    val analyzer: String? = null,
    val topN: Int? = null
)

/**
 * 분석 응답
 */
data class AnalyzeResponse(
    val totalTokens: Int,
    val tokens: List<String>,
    val topKeywords: List<TokenFrequency>
)
