package com.javacafe.rtwcollector.snscrawler.controller

import com.javacafe.rtwcollector.snscrawler.dto.TwitterCrawlResponse
import com.javacafe.rtwcollector.snscrawler.service.TwitterCrawlerService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult

@RestController
@RequestMapping("/api/twitter")
class TwitterCrawlerController(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val twitterCrawlerService: TwitterCrawlerService
) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @GetMapping("/search")
    fun searchTweets(
        @RequestParam query: String,
        @RequestParam(defaultValue = "10") maxResults: Int
    ): DeferredResult<ResponseEntity<TwitterCrawlResponse>> {
        logger.info { "Twitter search request: query='$query', maxResults=$maxResults" }

        val deferredResult = DeferredResult<ResponseEntity<TwitterCrawlResponse>>(30000L) // 30초 타임아웃

        // 타임아웃 처리
        deferredResult.onTimeout {
            logger.warn { "Twitter search timed out: query='$query'" }
            deferredResult.setResult(
                ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(
                    TwitterCrawlResponse.error(
                        query = query,
                        maxResults = maxResults,
                        errorMessage = "Request timed out"
                    )
                )
            )
        }

        // 에러 처리
        deferredResult.onError { throwable ->
            logger.error(throwable) { "Twitter search request error: query='$query'" }
            deferredResult.setResult(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    TwitterCrawlResponse.error(
                        query = query,
                        maxResults = maxResults,
                        errorMessage = "Request processing error: ${throwable.message}"
                    )
                )
            )
        }

        // 비동기 처리 시작
        CoroutineScope(ioDispatcher).launch {
            val responseEntity = twitterCrawlerService.fetchAndProcessTweets(query, maxResults)
                .fold(
                    onSuccess = { result ->
                        val response = TwitterCrawlResponse.success(
                            query = query,
                            maxResults = maxResults,
                            actualCount = result.actualCount,
                            fileId = result.fileId,
                            fileSize = result.fileSize
                        )
                        ResponseEntity.ok(response)
                    },
                    onFailure = { exception ->
                        val response = when (exception) {
                            is IllegalArgumentException -> {
                                TwitterCrawlResponse.error(
                                    query = query,
                                    maxResults = maxResults,
                                    errorMessage = "Invalid request: ${exception.message}"
                                )
                            }
                            is IllegalStateException -> {
                                TwitterCrawlResponse.error(
                                    query = query,
                                    maxResults = maxResults,
                                    errorMessage = exception.message ?: "No tweets found"
                                )
                            }
                            else -> {
                                TwitterCrawlResponse.error(
                                    query = query,
                                    maxResults = maxResults,
                                    errorMessage = "Processing failed: ${exception.message}"
                                )
                            }
                        }

                        val httpStatus = when (exception) {
                            is IllegalArgumentException -> HttpStatus.BAD_REQUEST
                            is IllegalStateException -> HttpStatus.NOT_FOUND
                            else -> HttpStatus.INTERNAL_SERVER_ERROR
                        }

                        ResponseEntity.status(httpStatus).body(response)
                    }
                )

            deferredResult.setResult(responseEntity)
        }

        return deferredResult
    }

}
