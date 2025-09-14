package com.javacafe.rtwcollector.rsscrawler.controller

import com.javacafe.rtwcollector.rsscrawler.dto.CrawlStatus
import com.javacafe.rtwcollector.rsscrawler.dto.CrawlSummary
import com.javacafe.rtwcollector.rsscrawler.dto.NewsCrawlResponse
import com.javacafe.rtwcollector.rsscrawler.service.NewsRssCrawlerService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/news")
class NewsRssController(
    private val newsRssCrawlerService: NewsRssCrawlerService
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @PostMapping("/crawl")
    suspend fun crawlNews(): ResponseEntity<NewsCrawlResponse> =
        runCatching {
            logger.info { "Starting news crawl request" }
            newsRssCrawlerService.crawlAllNews()
        }.fold(
            onSuccess = { summary -> createSuccessResponse(summary) },
            onFailure = { exception -> createErrorResponse(exception) }
        ).also { response ->
            logCrawlResult(response.body?.status, response.body?.totalSources ?: 0)
        }

    private fun createSuccessResponse(summary: CrawlSummary): ResponseEntity<NewsCrawlResponse> {
        val response = when {
            summary.isFullySuccessful -> {
                NewsCrawlResponse.success(
                    totalSources = summary.totalSources,
                    successfulSources = summary.successfulSources,
                    failedSources = summary.failedSources
                )
            }
            summary.hasPartialSuccess -> {
                NewsCrawlResponse.partialSuccess(
                    totalSources = summary.totalSources,
                    successfulSources = summary.successfulSources,
                    failedSources = summary.failedSources,
                    errors = summary.errors
                )
            }
            else -> {
                NewsCrawlResponse.error(
                    message = "All news sources failed to crawl",
                    errors = summary.errors
                )
            }
        }

        val httpStatus = when (response.status) {
            CrawlStatus.SUCCESS -> HttpStatus.OK
            CrawlStatus.PARTIAL_SUCCESS -> HttpStatus.PARTIAL_CONTENT
            CrawlStatus.ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
        }

        return ResponseEntity.status(httpStatus).body(response)
    }

    private fun createErrorResponse(exception: Throwable): ResponseEntity<NewsCrawlResponse> {
        logger.error(exception) { "News crawl failed with exception" }

        val response = NewsCrawlResponse.error(
            message = "News crawling failed: ${exception.message}",
            errors = listOf(exception.message ?: "Unknown error")
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }

    private fun logCrawlResult(status: CrawlStatus?, totalSources: Int) {
        when (status) {
            CrawlStatus.SUCCESS -> logger.info { "News crawl completed successfully for $totalSources sources" }
            CrawlStatus.PARTIAL_SUCCESS -> logger.warn { "News crawl completed with some failures for $totalSources sources" }
            CrawlStatus.ERROR -> logger.error { "News crawl failed completely" }
            null -> logger.error { "Unknown crawl result status" }
        }
    }
}
