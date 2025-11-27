package com.javacafe.rtwdataclean.consumer

import com.javacafe.rtwdataclean.model.InputMessage
import com.javacafe.rtwdataclean.producer.RefinedDataProducer
import com.javacafe.rtwdataclean.service.RefinementService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
class DataRefinementConsumer(
    private val refinementService: RefinementService,
    private val refinedDataProducer: RefinedDataProducer
) {

    @KafkaListener(
        topics = ["rss-items"],
        groupId = "rtw-dataclean-rss",
        concurrency = "10",
//        containerFactory = "rssKafkaListenerContainerFactory"
    )
    suspend fun consumeRss(
        @Payload records: List<ConsumerRecord<String, InputMessage>>,
        acknowledgment: Acknowledgment
    ) = processBatch(records, acknowledgment, "rss-items")

    @KafkaListener(
        topics = ["wiki-items"],
        groupId = "rtw-dataclean-wiki",
        concurrency = "10",
//        containerFactory = "wikiKafkaListenerContainerFactory"
    )
    suspend fun consumeWiki(
        @Payload records: List<ConsumerRecord<String, InputMessage>>,
        acknowledgment: Acknowledgment
    ) = processBatch(records, acknowledgment, "wiki-items")

    @KafkaListener(
        topics = ["twitter-items"],
        groupId = "rtw-dataclean-twitter",
        concurrency = "10",
//        containerFactory = "twitterKafkaListenerContainerFactory"
    )
    suspend fun consumeTwitter(
        @Payload records: List<ConsumerRecord<String, InputMessage>>,
        acknowledgment: Acknowledgment
    ) = processBatch(records, acknowledgment, "twitter-items")

    /**
     * Dispatcher 설정
     * - 정제 로직(RefinementService)은 CPU와 I/O가 섞여있을 수 있으므로 제한된 병렬성 적용
     * - Kafka 전송은 I/O 중심이므로 별도 디스패처
     */
    private val processingDispatcher = Dispatchers.IO.limitedParallelism(30)
    private val producerDispatcher = Dispatchers.IO.limitedParallelism(30)

    private suspend fun processBatch(
        records: List<ConsumerRecord<String, InputMessage>>,
        acknowledgment: Acknowledgment,
        sourceType: String
    ) = coroutineScope {
        if (records.isEmpty()) return@coroutineScope

        val batchId = UUID.randomUUID().toString()
        val hostname = System.getenv("HOSTNAME") ?: "unknown"
        val startTime = System.currentTimeMillis()

        logger.info {
            "[$batchId][$sourceType] Received batch: size=${records.size}, " +
                    "partition=${records.firstOrNull()?.partition()}, pod=$hostname"
        }

        runCatching {
            // 1️⃣ InputMessage 추출
            val messages = records.map { it.value() }

            // 2️⃣ 정제 (CPU 바운드)
            val refined = withContext(processingDispatcher) {
                messages
                    .map { async { refinementService.processMessages(listOf(it), sourceType) } }
                    .awaitAll()
                    .flatten()
            }

            // 3️⃣ 전송 (IO 바운드)
            val sentCount = withContext(producerDispatcher) {
                refinedDataProducer.sendBatch(sourceType = sourceType, refinedDataList = refined)
            }

            // 4️⃣ 커밋 및 로깅
            val totalTime = System.currentTimeMillis() - startTime
            val avgTime = totalTime / records.size.coerceAtLeast(1)

            when {
                refined.size == sentCount && sentCount == records.size -> {
                    acknowledgment.acknowledge()
                    logger.info {
                        "[$batchId][$sourceType] ✅ Success: size=${records.size}, " +
                                "processed=${refined.size}, sent=$sentCount, " +
                                "duration=${totalTime}ms (avg=${avgTime}ms)"
                    }
                }
                else -> logger.warn {
                    "[$batchId][$sourceType] ⚠️ Partial failure: processed=${refined.size}, sent=$sentCount. Not committing offset."
                }
            }
        }.onFailure { e ->
            logger.error(e) { "[$batchId][$sourceType] ❌ Batch failed: size=${records.size}" }
            // 예외 시 acknowledgment 수행하지 않음 → 재처리
        }
    }
}
