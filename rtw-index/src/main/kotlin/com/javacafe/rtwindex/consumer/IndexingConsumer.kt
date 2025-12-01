package com.javacafe.rtwindex.consumer

import com.javacafe.rtwindex.model.IndexDocument
import com.javacafe.rtwindex.model.RefinedDataMessage
import com.javacafe.rtwindex.model.SourceType
import com.javacafe.rtwindex.repository.FullTextRepository
import com.javacafe.rtwindex.service.IndexService
import com.javacafe.rtwindex.service.KeywordExtractionService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * 색인 Consumer
 * 
 * 처리 흐름:
 * 1. Kafka에서 RefinedDataMessage 배치 수신
 * 2. MongoDB에서 refinedId로 풀텍스트 조회
 * 3. 형태소 분석 및 키워드 추출
 * 4. Elasticsearch에 Bulk 색인
 * 5. 오프셋 커밋
 * 
 * 성능 목표: 초당 1,000건 처리
 */
@Component
class IndexingConsumer(
    private val fullTextRepository: FullTextRepository,
    private val indexService: IndexService,
    private val keywordExtractionService: KeywordExtractionService
) {
    /**
     * 처리용 Dispatcher (IO + 제한된 병렬성)
     */
    private val processingDispatcher = Dispatchers.IO.limitedParallelism(30)

    /**
     * RSS 토픽 Consumer
     */
    @KafkaListener(
        topics = ["refined-rss"],
        groupId = "\${app.kafka.consumer.group-id}-rss",
        containerFactory = "rssKafkaListenerContainerFactory"
    )
    fun consumeRss(
        records: List<ConsumerRecord<String, RefinedDataMessage>>,
        acknowledgment: Acknowledgment
    ) {
        processBatch(records, acknowledgment, SourceType.RSS)
    }

    /**
     * Wiki 토픽 Consumer
     */
    @KafkaListener(
        topics = ["refined-wiki"],
        groupId = "\${app.kafka.consumer.group-id}-wiki",
        containerFactory = "wikiKafkaListenerContainerFactory"
    )
    fun consumeWiki(
        records: List<ConsumerRecord<String, RefinedDataMessage>>,
        acknowledgment: Acknowledgment
    ) {
        processBatch(records, acknowledgment, SourceType.WIKI)
    }

    /**
     * Twitter 토픽 Consumer
     */
    @KafkaListener(
        topics = ["refined-twitter"],
        groupId = "\${app.kafka.consumer.group-id}-twitter",
        containerFactory = "twitterKafkaListenerContainerFactory"
    )
    fun consumeTwitter(
        records: List<ConsumerRecord<String, RefinedDataMessage>>,
        acknowledgment: Acknowledgment
    ) {
        processBatch(records, acknowledgment, SourceType.TWITTER)
    }

    /**
     * 배치 처리 메인 로직
     */
    private fun processBatch(
        records: List<ConsumerRecord<String, RefinedDataMessage>>,
        acknowledgment: Acknowledgment,
        sourceType: SourceType
    ) {
        if (records.isEmpty()) {
            acknowledgment.acknowledge()
            return
        }

        val batchId = UUID.randomUUID().toString().take(8)
        val startTime = System.currentTimeMillis()
        val hostname = System.getenv("HOSTNAME") ?: "local"

        logger.info {
            "[$batchId][${sourceType.name}] 📥 Received batch: size=${records.size}, " +
                "partition=${records.firstOrNull()?.partition()}, pod=$hostname"
        }

        runBlocking {
            runCatching {
                // 1️⃣ 메시지에서 refinedId 추출
                val messages = records.map { it.value() }
                val refinedIds = messages.map { it.refinedId }

                // 2️⃣ MongoDB에서 풀텍스트 배치 조회
                val fullTextDocs = withContext(processingDispatcher) {
                    fullTextRepository.findByRefinedIdIn(refinedIds)
                }

                if (fullTextDocs.isEmpty()) {
                    logger.warn { "[$batchId][${sourceType.name}] ⚠️ No documents found in MongoDB for ${refinedIds.size} IDs" }
                    acknowledgment.acknowledge()
                    return@runBlocking
                }

                logger.debug { "[$batchId][${sourceType.name}] 📄 Fetched ${fullTextDocs.size} documents from MongoDB" }

                // 3️⃣ IndexDocument로 변환 + 키워드 추출
                val indexDocuments = withContext(processingDispatcher) {
                    fullTextDocs.map { doc ->
                        async {
                            val baseDoc = IndexDocument.from(doc)
                            
                            // 키워드 추출
                            val keywordResult = keywordExtractionService.extractKeywords(
                                text = doc.fullText,
                                maxKeywords = 50
                            )
                            
                            baseDoc.copy(
                                keywords = keywordResult.map { it.word },
                                keywordScores = keywordResult
                            )
                        }
                    }.awaitAll()
                }

                // 4️⃣ Elasticsearch Bulk 색인
                val indexedCount = withContext(processingDispatcher) {
                    indexService.bulkIndex(indexDocuments)
                }

                // 5️⃣ 결과 로깅 및 커밋
                val duration = System.currentTimeMillis() - startTime
                val avgTime = duration / records.size.coerceAtLeast(1)
                val throughput = (records.size * 1000.0 / duration).let { "%.1f".format(it) }

                if (indexedCount == indexDocuments.size) {
                    acknowledgment.acknowledge()
                    logger.info {
                        "[$batchId][${sourceType.name}] ✅ Success: " +
                            "received=${records.size}, fetched=${fullTextDocs.size}, indexed=$indexedCount, " +
                            "duration=${duration}ms, avg=${avgTime}ms/doc, throughput=$throughput docs/sec"
                    }
                } else {
                    logger.warn {
                        "[$batchId][${sourceType.name}] ⚠️ Partial: " +
                            "received=${records.size}, fetched=${fullTextDocs.size}, indexed=$indexedCount. " +
                            "Not committing offset for retry."
                    }
                }

            }.onFailure { e ->
                val duration = System.currentTimeMillis() - startTime
                logger.error(e) {
                    "[$batchId][${sourceType.name}] ❌ Failed: size=${records.size}, duration=${duration}ms"
                }
                // 커밋하지 않음 → 재처리
            }
        }
    }
}
