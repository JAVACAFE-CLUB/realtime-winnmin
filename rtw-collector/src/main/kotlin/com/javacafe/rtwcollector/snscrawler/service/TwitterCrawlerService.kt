package com.javacafe.rtwcollector.snscrawler.service

import com.javacafe.rtwcollector.common.infra.CollectorKafkaProduceMessage
import com.javacafe.rtwcollector.common.infra.OriginDataStorageFileManager
import com.javacafe.rtwcollector.snscrawler.dto.TwitterProcessResult
import com.javacafe.rtwcollector.snscrawler.processor.TwitterApiCallRequestor
import com.javacafe.rtwcore.constants.CollectorConstant
import com.javacafe.rtwcore.infra.KafkaMessageProducer
import com.javacafe.rtwcore.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

@Service
class TwitterCrawlerService(
    private val twitterApiCallRequestor: TwitterApiCallRequestor,
    private val originDataStorageFileManager: OriginDataStorageFileManager,
    private val kafkaMessageProducer: KafkaMessageProducer
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    suspend fun fetchAndProcessTweets(
        query: String,
        maxResults: Int = 10
    ): Result<TwitterProcessResult> = coroutineScope {
        runCatching {
            logger.debug { "Fetching tweets for query: '$query' (max: $maxResults)" }

            // 입력 검증
            require(query.isNotBlank()) { "Query cannot be blank" }
            require(maxResults in 1..100) { "Max results must be between 1 and 100" }

            val apiResult = twitterApiCallRequestor.fetchTwitterContent(query)

            if (apiResult.data.isNullOrEmpty()) {
                logger.warn { "No tweets found for query: '$query'" }
                throw IllegalStateException("No tweets found for query: '$query'")
            }

            val fileWriteResult = originDataStorageFileManager.saveParsedOriginDataToFile(
                originData = apiResult,
                prefix = CollectorConstant.TWITTER_CRAWL_PREFIX
            ).getOrThrow()

            val metadata = CollectorKafkaProduceMessage(
                fileId = fileWriteResult.fileId,
                filePath = fileWriteResult.path,
                fileSize = fileWriteResult.fileSize,
                fileChecksum = fileWriteResult.checksum,
                source = CollectorConstant.CRAWL_TYPE_TWITTER_API,
                timestamp = DateTimeUtils.localDateTimeNowAsStr(),
            )

            kafkaMessageProducer.sendCollectorCrawledMetadata(
                messageId = metadata.fileId,
                payload = metadata,
                topic = CollectorConstant.TWITTER_KAFKA_TOPIC
            )

            logger.info {
                "Successfully processed Twitter data: ${metadata.fileId} " +
                        "(query: '$query', ${apiResult.data.size} tweets, ${metadata.fileSize} bytes)"
            }

            TwitterProcessResult(
                fileId = fileWriteResult.fileId,
                fileSize = fileWriteResult.fileSize,
                actualCount = apiResult.data.size,
                query = query,
                maxResults = maxResults
            )

        }.onFailure { exception ->
            logger.error(exception) {
                "Failed to fetch and process tweets for query: '$query' (max: $maxResults)"
            }
        }
    }
}
