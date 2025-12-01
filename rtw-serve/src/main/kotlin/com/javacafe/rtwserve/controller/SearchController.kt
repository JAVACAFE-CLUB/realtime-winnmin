package com.javacafe.rtwserve.controller

import com.javacafe.rtwserve.application.dto.ArticleDto
import com.javacafe.rtwserve.application.dto.AutocompleteResponse
import com.javacafe.rtwserve.application.dto.SearchRequest
import com.javacafe.rtwserve.application.dto.SearchResponse
import com.javacafe.rtwserve.application.service.SearchService
import com.javacafe.rtwserve.common.dto.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * 검색 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/search")
class SearchController(
    private val searchService: SearchService
) {
    /**
     * 통합 검색
     * 
     * GET /api/v1/search?q={query}&page=0&size=20
     */
    @GetMapping
    fun search(
        @RequestParam("q") query: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sourceType: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) fromDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) toDate: LocalDateTime?,
        @RequestParam(defaultValue = "relevance") sortBy: String
    ): ResponseEntity<ApiResponse<SearchResponse>> {
        logger.debug { "Search request: query=$query, page=$page, size=$size" }
        
        val request = SearchRequest(
            query = query,
            page = page,
            size = size,
            sourceType = sourceType,
            category = category,
            fromDate = fromDate,
            toDate = toDate,
            sortBy = sortBy
        )
        
        val result = searchService.search(request)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    /**
     * 고급 검색 (POST)
     * 
     * POST /api/v1/search/advanced
     */
    @PostMapping("/advanced")
    fun advancedSearch(@RequestBody request: SearchRequest): ResponseEntity<ApiResponse<SearchResponse>> {
        logger.debug { "Advanced search request: $request" }
        
        val result = searchService.search(request)
        return ResponseEntity.ok(ApiResponse.success(result))
    }

    /**
     * 자동완성
     * 
     * GET /api/v1/search/autocomplete?q={prefix}
     */
    @GetMapping("/autocomplete")
    fun autocomplete(
        @RequestParam("q") prefix: String,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<ApiResponse<AutocompleteResponse>> {
        val result = searchService.autocomplete(prefix, size)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}

/**
 * 문서 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/articles")
class ArticleController(
    private val searchService: SearchService
) {
    /**
     * 문서 상세 조회
     * 
     * GET /api/v1/articles/{id}
     */
    @GetMapping("/{id}")
    fun getArticle(@PathVariable id: String): ResponseEntity<ApiResponse<ArticleDto?>> {
        val article = searchService.getArticle(id)
        return if (article != null) {
            ResponseEntity.ok(ApiResponse.success(article))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * 최신 문서 조회
     * 
     * GET /api/v1/articles/recent?size=20&sourceType=RSS
     */
    @GetMapping("/recent")
    fun getRecentArticles(
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sourceType: String?
    ): ResponseEntity<ApiResponse<List<ArticleDto>>> {
        val articles = searchService.getRecentArticles(size, sourceType)
        return ResponseEntity.ok(ApiResponse.success(articles))
    }

    /**
     * 소스별 문서 조회
     * 
     * GET /api/v1/articles/source/{type}
     */
    @GetMapping("/source/{type}")
    fun getArticlesBySource(
        @PathVariable type: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<SearchResponse>> {
        val request = SearchRequest(
            query = "*",
            page = page,
            size = size,
            sourceType = type,
            sortBy = "date"
        )
        val result = searchService.search(request)
        return ResponseEntity.ok(ApiResponse.success(result))
    }
}
