package com.javacafe.rtwcollector.filecrawler.service

import com.javacafe.rtwcollector.common.infra.CollectorKafkaProduceMessage
import com.javacafe.rtwcollector.common.infra.OriginDataStorageFileManager
import com.javacafe.rtwcollector.filecrawler.dto.ChunkResult
import com.javacafe.rtwcollector.filecrawler.dto.ProcessingResult
import com.javacafe.rtwcollector.filecrawler.model.WikiPage
import com.javacafe.rtwcollector.filecrawler.processor.WikiXmlStreamParser
import com.javacafe.rtwcore.constants.CollectorConstant
import com.javacafe.rtwcore.infra.KafkaMessageProducer
import com.javacafe.rtwcore.utils.DateTimeUtils
import com.javacafe.rtwcore.utils.chunked
import com.javacafe.rtwcore.utils.mapIndexed
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths

@Service
class WikiFileCrawlService(
    private val ioDispatcher: CoroutineDispatcher,
    private val kafkaMessageProducer: KafkaMessageProducer,
    private val originDataStorageFileManager: OriginDataStorageFileManager,
    private val wikiXmlStreamParser: WikiXmlStreamParser
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    suspend fun processWikiFile(filePath: String): ProcessingResult = coroutineScope {
        logger.info { "Starting Wiki file processing: $filePath" }

        val path = Paths.get(filePath).also {
            require(Files.exists(it)) { "File does not exist: $it" }
            require(Files.isReadable(it)) { "File is not readable: $it" }
            require(Files.size(it) > 0) { "File is empty: $it" }
        }
        var totalPages = 0
        var totalChunks = 0
        val errors = mutableListOf<String>()

        runCatching {
            // 1. 호출한 스레드에서 실행
            wikiXmlStreamParser.parsePages(path)
                .flowOn(ioDispatcher) // 2. IO 스레드로 전환
                .buffer(CollectorConstant.WIKI_CHUNK_PER_SIZE * 2)
                .chunked(CollectorConstant.WIKI_CHUNK_PER_SIZE)
                .mapIndexed { chunkIndex, pages ->
                    async { processChunk(pages, chunkIndex) } // 3. 각 청크마다 새로운 코루틴
                }
                .buffer(10) // 동시 처리 제한
                .collect { deferredResult ->
                    // 4. 각 async 결과를 기다림
                    deferredResult.await().fold(
                        onSuccess = { chunkResult ->
                            totalPages += chunkResult.pageCount
                            totalChunks++
                            logger.debug {
                                "Chunk ${chunkResult.chunkIndex} completed: ${chunkResult.pageCount} pages"
                            }
                        },
                        onFailure = { exception ->
                            errors.add("Chunk processing failed: ${exception.message}")
                            logger.error(exception) {
                                "Failed to process chunk in file: $filePath"
                            }
                        }
                    )
                }
        }.onFailure { exception ->
            errors.add("File processing failed: ${exception.message}")
            logger.error(exception) { "Failed to process Wiki file: $filePath" }
        }

        val result = ProcessingResult(
            totalPages = totalPages,
            totalChunks = totalChunks,
            errors = errors
        )
        logger.info {
            "Wiki file processing completed: $filePath " +
                    "(${result.totalPages} pages, ${result.totalChunks} chunks, ${result.errors.size} errors)"
        }

        result
    }

    private suspend fun processChunk(
        pages: List<WikiPage>,
        chunkIndex: Int
    ): Result<ChunkResult> = runCatching {
        logger.debug { "Processing chunk $chunkIndex with ${pages.size} pages" }

        val fileWriteResult = originDataStorageFileManager.saveParsedOriginDataToFile(
            originData = pages,
            prefix = CollectorConstant.WIKI_CRAWL_PREFIX
        )

        fileWriteResult.getOrThrow().let { fileInfo ->
            val metadata = CollectorKafkaProduceMessage(
                fileId = fileInfo.fileId,
                filePath = fileInfo.path,
                fileSize = fileInfo.fileSize,
                fileChecksum = fileInfo.checksum,
                source = CollectorConstant.CRAWL_TYPE_WIKI_FILE,
                timestamp = DateTimeUtils.localDateTimeNowAsStr(),
            )

            kafkaMessageProducer.sendCollectorCrawledMetadata(
                messageId = metadata.fileId,
                payload = metadata,
                topic = CollectorConstant.WIKI_KAFKA_TOPIC
            )

            logger.info {
                "Successfully processed chunk $chunkIndex: ${metadata.fileId} " +
                        "(${pages.size} pages, ${metadata.fileSize} bytes)"
            }

            ChunkResult(
                chunkIndex = chunkIndex,
                pageCount = pages.size,
                fileId = fileInfo.fileId,
                fileSize = fileInfo.fileSize
            )
        }
    }
}
