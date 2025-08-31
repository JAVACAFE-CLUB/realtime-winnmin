package com.javacafe.rtwserve.controller

import com.javacafe.rtwserve.application.dto.NewsCreateRequest
import com.javacafe.rtwserve.application.dto.NewsResponse
import com.javacafe.rtwserve.application.dto.NewsSearchRequest
import com.javacafe.rtwserve.application.service.NewsService
import com.javacafe.rtwserve.common.dto.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/news")
class NewsController(
    private val newsService: NewsService
) {

    @GetMapping("/search")
    fun searchNews(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "manual") source: String = "manual",
        @RequestParam(defaultValue = "10") size: Int = 10
    ): ResponseEntity<ApiResponse<List<NewsResponse>>> {
        val searchRequest = NewsSearchRequest(
            keyword = keyword,
            source = source,
            size = size
        )

        val searchResults = newsService.searchNewsWithLimit(searchRequest)

        return ResponseEntity.ok(
            ApiResponse.success(
                data = searchResults,
                message = "키워드 '$keyword'로 ${searchResults.size}개의 뉴스를 찾았습니다."
            )
        )
    }

    @PostMapping
    fun createNews(@RequestBody request: NewsCreateRequest): ResponseEntity<ApiResponse<NewsResponse>> {
        val newsResponse = newsService.createNews(request)

        return ResponseEntity.ok(
            ApiResponse.success(
                data = newsResponse,
                message = "뉴스 기사가 성공적으로 저장되었습니다."
            )
        )
    }

    @DeleteMapping("/{id}")
    fun deleteNews(@PathVariable id: String): ResponseEntity<ApiResponse<String>> {
        newsService.deleteNews(id) // Boolean이 아닌 성공 시에만 실행됨

        return ResponseEntity.ok(
            ApiResponse.success(
                data = "삭제 완료",
                message = "뉴스 기사가 성공적으로 삭제되었습니다."
            )
        )
    }
}
