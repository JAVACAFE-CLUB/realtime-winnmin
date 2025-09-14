package com.javacafe.rtwcollector.rsscrawler.service

import com.javacafe.rtwcollector.common.constants.CollectorConstant
import com.javacafe.rtwcollector.common.infra.CollectorKafkaProduceMessage
import com.javacafe.rtwcollector.rsscrawler.processor.HttpClientComponent
import com.javacafe.rtwcollector.common.infra.KafkaMessageProducer
import com.javacafe.rtwcollector.common.infra.OriginDataStorageFileManager
import com.javacafe.rtwcollector.common.utils.DateTimeUtils
import com.javacafe.rtwcollector.rsscrawler.dto.CrawlResult
import com.javacafe.rtwcollector.rsscrawler.dto.CrawlSummary
import com.javacafe.rtwcollector.rsscrawler.model.NewsItem
import com.javacafe.rtwcollector.rsscrawler.processor.RssParser
import com.javacafe.rtwcollector.rsscrawler.processor.RssSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class NewsRssCrawlerService(
    private val httpClientComponent: HttpClientComponent,
    private val rssParser: RssParser,
    private val kafkaMessageProducer: KafkaMessageProducer,
    private val originDataStorageFileManager: OriginDataStorageFileManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    suspend fun crawlAllNews(): CrawlSummary = coroutineScope {
        val enabledSources = RssSource.getEnabledSources()
        logger.info { "Starting news crawl for ${enabledSources.size} sources" }

        if (enabledSources.isEmpty()) {
            logger.warn { "No enabled news sources found" }
            return@coroutineScope CrawlSummary(0, emptyList())
        }

        val results = enabledSources
            .asFlow()
            .flatMapMerge(concurrency = CollectorConstant.RSS_MAX_CONCURRENT_CRAWLS) { source ->
                flow {
                    emit(crawlNewsFromSourceSafely(source))
                }.flowOn(ioDispatcher)
            }
            .toList()

        val summary = CrawlSummary(enabledSources.size, results)
        logCrawlSummary(summary)

        summary
    }

    private suspend fun crawlNewsFromSourceSafely(source: RssSource): CrawlResult =
        runCatching {
            logger.info { "Fetching RSS from: ${source.sourceName} - ${source.category}" }

            val xmlContent = httpClientComponent.fetchContent(source.url)
            val parseResult = rssParser.parseRssXml(xmlContent, source)

            if (parseResult.items.isEmpty()) {
                logger.warn { "No items found for ${source.sourceName} - ${source.category}" }
                return@runCatching CrawlResult(
                    source = source,
                    success = true,
                    itemCount = 0
                )
            }

            val totalProcessed = processNewsItems(parseResult.items, source)

            CrawlResult(
                source = source,
                success = true,
                itemCount = totalProcessed
            )

        }.fold(
            onSuccess = { result ->
                logger.debug { "Successfully processed ${result.itemCount} items from ${source.sourceName}" }
                result
            },
            onFailure = { exception ->
                val errorMessage = "Failed to crawl from ${source.sourceName}: ${exception.message}"
                logger.error(exception) { errorMessage }
                CrawlResult(
                    source = source,
                    success = false,
                    error = errorMessage
                )
            }
        )

    private suspend fun processNewsItems(items: List<NewsItem>, source: RssSource): Int {
        var totalProcessed = 0

        items.chunked(CollectorConstant.RSS_CHUNK_SIZE).forEach { chunk ->
            processNewsChunk(chunk, source).fold(
                onSuccess = { processedCount ->
                    totalProcessed += processedCount
                },
                onFailure = { exception ->
                    logger.error(exception) {
                        "Failed to process chunk of ${chunk.size} items from ${source.sourceName}"
                    }
                    // 청크 실패해도 다음 청크는 계속 처리
                }
            )
        }

        return totalProcessed
    }

    private suspend fun processNewsChunk(
        items: List<NewsItem>,
        source: RssSource
    ): Result<Int> = runCatching {
        logger.debug { "Processing ${items.size} news items from ${source.sourceName} - ${source.category}" }

        val fileWriteResult = originDataStorageFileManager.saveParsedOriginDataToFile(
            originData = items,
            prefix = CollectorConstant.RSS_CRAWL_PREFIX
        ).getOrThrow()

        val metadata = CollectorKafkaProduceMessage(
            fileId = fileWriteResult.fileId,
            filePath = fileWriteResult.path,
            fileSize = fileWriteResult.fileSize,
            fileChecksum = fileWriteResult.checksum,
            source = CollectorConstant.CRAWL_TYPE_NEWS_RSS,
            timestamp = DateTimeUtils.localDateTimeNowAsStr(),
        )

        kafkaMessageProducer.sendCollectorCrawledMetadata(
            messageId = metadata.fileId,
            payload = metadata,
            topic = CollectorConstant.RSS_KAFKA_TOPIC
        )

        logger.info {
            "Successfully processed chunk: ${metadata.fileId} " +
                    "(${items.size} items, ${metadata.fileSize} bytes) " +
                    "from ${source.sourceName}"
        }

        items.size
    }

    private fun logCrawlSummary(summary: CrawlSummary) {
        logger.info {
            "Completed news crawl: ${summary.successfulSources}/${summary.totalSources} sources successful, " +
                    "${summary.totalItems} total items processed"
        }

        if (summary.failedSources > 0) {
            logger.warn { "Failed sources: ${summary.failedSources}" }
            summary.errors.forEach { error ->
                logger.debug { "Error details: $error" }
            }
        }
    }
}
