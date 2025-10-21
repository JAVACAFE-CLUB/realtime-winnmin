package com.javacafe.rtwcollector.snscrawler.service

import com.javacafe.rtwcollector.common.infra.CollectorKafkaProduceMessage
import com.javacafe.rtwcollector.common.infra.OriginDataStorageManager
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
    private val originDataStorageManager: OriginDataStorageManager,  // ✅ 인터페이스로 변경
    private val kafkaMessageProducer: KafkaMessageProducer
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * 트윗 수집 및 처리 (단건 저장 방식)
     *
     * 각 트윗을 개별 Document로 MongoDB에 저장
     */
    suspend fun fetchAndProcessTweets(
        query: String,
        maxResults: Int = 10
    ): Result<TwitterProcessResult> = coroutineScope {
        runCatching {
            logger.debug { "Fetching tweets for query: '$query' (max: $maxResults)" }

            // 1. 입력 검증
            require(query.isNotBlank()) { "Query cannot be blank" }
            require(maxResults in 1..100) { "Max results must be between 1 and 100" }

            // 2. Twitter API 호출
            val apiResult = twitterApiCallRequestor.fetchTwitterContent(query)

            if (apiResult.data.isNullOrEmpty()) {
                logger.warn { "No tweets found for query: '$query'" }
                throw IllegalStateException("No tweets found for query: '$query'")
            }

            logger.info {
                "Fetched ${apiResult.data.size} tweets for query: '$query', " +
                        "processing as individual documents..."
            }

            // 3. 각 트윗을 개별적으로 저장 및 Kafka 메시지 전송
            var successCount = 0
            var totalSize = 0L
            val storageResults = mutableListOf<String>()

            apiResult.data.forEach { tweet ->
                // 3-1. 단일 트윗 저장
                val storageResult = originDataStorageManager.saveSingleOriginData(
                    originData = tweet,
                    prefix = CollectorConstant.TWITTER_CRAWL_PREFIX,
                    metadata = mapOf(
                        "query" to query,
                        "maxResults" to maxResults,
                        "tweetId" to tweet.id,
                        "collectionTimestamp" to DateTimeUtils.localDateTimeNowAsStr()
                    )
                ).getOrElse { exception ->
                    logger.warn(exception) {
                        "Failed to save tweet (id: ${tweet.id}), skipping..."
                    }
                    return@forEach  // 실패한 트윗은 스킵하고 다음으로
                }

                // 3-2. Kafka 메시지 생성 및 전송
                val metadata = CollectorKafkaProduceMessage(
                    fileId = storageResult.id,
                    filePath = storageResult.storageLocation,
                    fileSize = storageResult.dataSize,
                    fileChecksum = storageResult.checksum,
                    source = CollectorConstant.CRAWL_TYPE_TWITTER_API,
                    timestamp = DateTimeUtils.localDateTimeNowAsStr(),
                )

                kafkaMessageProducer.sendCollectorCrawledMetadata(
                    messageId = metadata.fileId,
                    payload = metadata,
                    topic = CollectorConstant.TWITTER_KAFKA_TOPIC
                )

                successCount++
                totalSize += storageResult.dataSize
                storageResults.add(storageResult.id)

                logger.debug {
                    "Saved tweet: ${storageResult.id} " +
                            "(tweetId: ${tweet.id}, ${storageResult.dataSize} bytes)"
                }
            }

            logger.info {
                "Successfully processed Twitter data: $successCount/${apiResult.data.size} tweets " +
                        "(query: '$query', total size: $totalSize bytes)"
            }

            // 4. 결과 반환
            TwitterProcessResult(
                fileId = storageResults.joinToString(","),  // 여러 ID를 콤마로 구분
                fileSize = totalSize,
                actualCount = successCount,
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
