package com.javacafe.rtwcollector.filecrawler.service

import com.javacafe.rtwcollector.common.infra.CollectorKafkaProduceMessage
import com.javacafe.rtwcollector.common.infra.OriginDataStorageManager
import com.javacafe.rtwcollector.filecrawler.dto.ProcessingResult
import com.javacafe.rtwcollector.filecrawler.model.WikiPage
import com.javacafe.rtwcollector.filecrawler.processor.WikiXmlStreamParser
import com.javacafe.rtwcore.constants.CollectorConstant
import com.javacafe.rtwcore.infra.KafkaMessageProducer
import com.javacafe.rtwcore.utils.DateTimeUtils
import com.javacafe.rtwcore.utils.chunked
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
    private val originDataStorageManager: OriginDataStorageManager,
    private val wikiXmlStreamParser: WikiXmlStreamParser
) {

    companion object {
        private val logger = KotlinLogging.logger { }
        private const val BATCH_SIZE = 50
        private const val MAX_CONCURRENT_BATCHES = 5
    }

    suspend fun processWikiFile(filePath: String): ProcessingResult = coroutineScope {
        logger.info {
            "Starting Wiki file processing (batch parallel mode): $filePath " +
                    "(batch size: $BATCH_SIZE, max concurrent: $MAX_CONCURRENT_BATCHES)"
        }

        val path = Paths.get(filePath).also {
            require(Files.exists(it)) { "File does not exist: $it" }
            require(Files.isReadable(it)) { "File is not readable: $it" }
            require(Files.size(it) > 0) { "File is empty: $it" }
        }

        var totalPages = 0
        var successCount = 0
        var failCount = 0
        val errors = mutableListOf<String>()

        runCatching {
            wikiXmlStreamParser.parsePages(path)
                .flowOn(ioDispatcher)
                .buffer(BATCH_SIZE * 2)
                .chunked(BATCH_SIZE)
                .map { batch ->
                    async {
                        totalPages += batch.size
                        processBatchParallel(batch, filePath)
                    }
                }
                .buffer(MAX_CONCURRENT_BATCHES)
                .collect { deferredResult ->
                    deferredResult.await().fold(
                        onSuccess = { savedCount ->
                            successCount += savedCount
                            logger.info {
                                "Progress: $successCount/$totalPages pages saved"
                            }
                        },
                        onFailure = { exception ->
                            failCount += BATCH_SIZE
                            errors.add("Batch processing failed: ${exception.message}")
                            logger.error(exception) {
                                "Failed to process batch from: $filePath"
                            }
                        }
                    )
                }
        }.onFailure { exception ->
            errors.add("File processing failed: ${exception.message}")
            logger.error(exception) { "Failed to process Wiki file: $filePath" }
        }

        val result = ProcessingResult(
            totalPages = successCount,
            totalChunks = 0,
            errors = errors
        )

        logger.info {
            "Wiki file processing completed: $filePath " +
                    "($successCount/$totalPages pages successful, $failCount failed)"
        }

        result
    }

    /**
     * 배치를 병렬로 저장
     */
    private suspend fun processBatchParallel(
        pages: List<WikiPage>,
        sourceFile: String
    ): Result<Int> = coroutineScope {  // ✅ coroutineScope 추가
        runCatching {
            logger.debug { "Processing batch of ${pages.size} pages in parallel" }

            // MongoDB 저장
            val results = originDataStorageManager.saveBatchOriginData(
                originDataList = pages,
                prefix = CollectorConstant.WIKI_CRAWL_PREFIX,
                metadata = mapOf(
                    "sourceFile" to sourceFile,
                    "collectionTimestamp" to DateTimeUtils.localDateTimeNowAsStr()
                )
            ).getOrThrow()

            // ✅ Kafka 메시지 병렬 전송
            results.forEach { storageWriteResult ->
                launch(Dispatchers.IO) {  // ✅ 이제 coroutineScope 내부라서 가능
                    val metadata = CollectorKafkaProduceMessage(
                        fileId = storageWriteResult.id,
                        filePath = storageWriteResult.storageLocation,
                        fileSize = storageWriteResult.dataSize,
                        fileChecksum = storageWriteResult.checksum,
                        source = CollectorConstant.CRAWL_TYPE_WIKI_FILE,
                        timestamp = DateTimeUtils.localDateTimeNowAsStr(),
                    )

                    kafkaMessageProducer.sendCollectorCrawledMetadata(
                        messageId = metadata.fileId,
                        payload = metadata,
                        topic = CollectorConstant.WIKI_KAFKA_TOPIC
                    )
                }
            }

            logger.info { "Successfully processed batch: ${results.size} pages saved" }
            results.size
        }
    }
}
