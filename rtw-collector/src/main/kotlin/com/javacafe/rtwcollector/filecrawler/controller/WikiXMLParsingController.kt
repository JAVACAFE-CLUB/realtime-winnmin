package com.javacafe.rtwcollector.filecrawler.controller

import com.javacafe.rtwcollector.filecrawler.dto.ProcessingResponse
import com.javacafe.rtwcollector.filecrawler.service.WikiFileCrawlService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.async.DeferredResult

@RestController
@RequestMapping("/api/wiki")
class WikiProcessorController(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val wikiFileCrawlService: WikiFileCrawlService
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @PostMapping("/process")
    fun processWikiFileAsync(
        @RequestParam filePath: String
    ): DeferredResult<ResponseEntity<ProcessingResponse>> {
        logger.info { "Processing wiki file async request: $filePath" }

        val deferredResult = DeferredResult<ResponseEntity<ProcessingResponse>>(30000L) // 30초 타임아웃

        // 비동기 처리 설정
        deferredResult.onTimeout {
            logger.warn { "Wiki file processing timed out: $filePath" }
            deferredResult.setResult(
                ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(
                    ProcessingResponse.error("Processing timed out")
                )
            )
        }

        deferredResult.onError { throwable ->
            logger.error(throwable) { "Wiki file processing error: $filePath" }
            deferredResult.setResult(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProcessingResponse.error("Processing failed: ${throwable.message}")
                )
            )
        }

        // 별도 스레드에서 처리
        CoroutineScope(ioDispatcher).launch {
            runCatching {
                wikiFileCrawlService.processWikiFile(filePath)
            }.fold(
                onSuccess = { result ->
                    val response = ProcessingResponse(
                        success = result.isSuccessful,
                        message = if (result.isSuccessful) "Processing completed successfully"
                        else "Processing completed with errors",
                        totalPages = result.totalPages,
                        totalChunks = result.totalChunks,
                        errors = result.errors,
                        summary = result.summary()
                    )

                    val httpStatus = if (result.isSuccessful) HttpStatus.OK else HttpStatus.PARTIAL_CONTENT
                    deferredResult.setResult(ResponseEntity.status(httpStatus).body(response))
                },
                onFailure = { exception ->
                    val errorResponse = when (exception) {
                        is IllegalArgumentException -> {
                            logger.error(exception) { "Invalid file path: $filePath" }
                            ResponseEntity.badRequest().body(
                                ProcessingResponse.error("Invalid file path: ${exception.message}")
                            )
                        }
                        else -> {
                            logger.error(exception) { "Failed to process wiki file: $filePath" }
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                                ProcessingResponse.error("Processing failed: ${exception.message}")
                            )
                        }
                    }
                    deferredResult.setResult(errorResponse)
                }
            )
        }

        return deferredResult
    }
}
