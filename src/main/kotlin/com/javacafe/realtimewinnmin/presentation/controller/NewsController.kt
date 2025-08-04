package com.javacafe.realtimewinnmin.presentation.controller

import com.javacafe.realtimewinnmin.application.dto.NewsCreateRequest
import com.javacafe.realtimewinnmin.application.dto.NewsListResponse
import com.javacafe.realtimewinnmin.application.dto.NewsResponse
import com.javacafe.realtimewinnmin.application.dto.NewsSearchRequest
import com.javacafe.realtimewinnmin.application.service.NewsService
import com.javacafe.realtimewinnmin.common.dto.ApiResponse
import com.javacafe.realtimewinnmin.common.exception.ExceptionCode
import com.javacafe.realtimewinnmin.common.exception.GlobalException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/news")
class NewsController(
    private val newsService: NewsService
) {
      private val logger = KotlinLogging.logger { }

    /**
     * 뉴스 기사 검색
     * GET /api/news/search?keyword=삼성전자&size=10
     */
    @GetMapping("/search")
    fun searchNews(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "manual") source: String = "manual",
        @RequestParam(defaultValue = "10") size: Int = 10
    ): ResponseEntity<ApiResponse<NewsListResponse>> {
        logger.info("GET /api/news/search - Searching news with keyword: $keyword")

        return runCatching {
            val searchRequest = NewsSearchRequest(
                keyword = keyword,
                source = source,
                size = size
            )

            newsService.searchNews(searchRequest)
        }.fold(
            onSuccess = { searchResults ->
                logger.info("Successfully found ${searchResults.news.size} news articles for keyword: $keyword")

                ResponseEntity.ok(
                    ApiResponse.success(
                        data = searchResults,
                        message = "키워드 '$keyword'로 ${searchResults.news.size}개의 뉴스를 찾았습니다."
                    )
                )
            },
            onFailure = { exception ->
                logger.error("Error searching news with keyword: $keyword")
                throw GlobalException(
                    ExceptionCode.INTERNAL_SERVER_ERROR,
                    "뉴스 검색 중 오류가 발생했습니다. ${exception.message}"
                )
            }
        )
    }
    
    /**
     * 뉴스 기사 저장
     * POST /api/news
     */
    @PostMapping
    fun createNews(@RequestBody request: NewsCreateRequest): ResponseEntity<ApiResponse<NewsResponse>> {
        logger.info("POST /api/news - Creating news: ${request.title}")

        return runCatching {
            newsService.createNews(request)
        }.fold(
            onSuccess = { newsResponse ->
                logger.info("Successfully created news with UUID: ${newsResponse.id}")
                ResponseEntity.ok(
                    ApiResponse.success(
                        data = newsResponse,
                        message = "뉴스 기사가 성공적으로 저장되었습니다."
                    )
                )
            },
            onFailure = { exception ->
                logger.error("Error creating news: ${request.title}", exception)
                throw GlobalException(ExceptionCode.INTERNAL_SERVER_ERROR, "뉴스 기사 저장 중 오류가 발생했습니다. $exception.message")
            }
        )
    }

    /**
     * 뉴스 기사 삭제
     * DELETE /api/news/{id}
     */
    @DeleteMapping("/{id}")
    fun deleteNews(@PathVariable id: String): ResponseEntity<ApiResponse<Boolean>> {
        logger.info("DELETE /api/news/$id - Deleting news article")

        return runCatching {
            newsService.deleteNews(id)
        }.fold(
            onSuccess = { deleted ->
                val (message, logMessage) = if (deleted) {
                    "뉴스 기사가 성공적으로 삭제되었습니다." to "Successfully deleted news article: $id"
                } else {
                    "삭제할 뉴스 기사를 찾을 수 없습니다." to "News article not found for deletion: $id"
                }

                logger.info(logMessage)
                ResponseEntity.ok(ApiResponse.success(data = deleted, message = message))
            },
            onFailure = { exception ->
                logger.error("Error deleting news article: $id", exception)
                throw GlobalException(
                     ExceptionCode.INTERNAL_SERVER_ERROR,
                    "뉴스 기사 삭제 중 오류가 발생했습니다. ${exception.message}"
                )
            }
        )
    }
}
