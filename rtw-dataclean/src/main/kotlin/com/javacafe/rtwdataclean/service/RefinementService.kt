package com.javacafe.rtwdataclean.service

import com.javacafe.rtwdataclean.model.FullTextDocument
import com.javacafe.rtwdataclean.model.InputMessage
import com.javacafe.rtwdataclean.model.RefinedData
import com.javacafe.rtwdataclean.model.SourceType
import com.javacafe.rtwdataclean.repository.FullTextDataRepository
import com.javacafe.rtwdataclean.repository.OriginDataRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class RefinementService(
    private val originDataRepository: OriginDataRepository,
    private val fullTextDataRepository: FullTextDataRepository,
    private val idGenerationService: IdGenerationService,
    private val fullTextExtractionService: FullTextExtractionService,
    private val metadataGenerationService: MetadataGenerationService,
    @Qualifier("dataCleanDispatcher") private val dispatcher: CoroutineDispatcher
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * 메시지 배치를 병렬로 정제 처리
     */
    suspend fun processMessages(
        messages: List<InputMessage>,
        topic: String
    ): List<RefinedData> = coroutineScope {
        if (messages.isEmpty()) return@coroutineScope emptyList()

        logger.info { "🧩 Processing batch: topic=$topic, size=${messages.size}" }

        val sourceType = runCatching { SourceType.fromTopic(topic) }
            .onFailure { e -> logger.error(e) { "❌ Unknown topic: $topic" } }
            .getOrNull() ?: return@coroutineScope emptyList()

        val results = withContext(dispatcher) {
            messages.parallelMap {
                runCatching { processMessage(it, sourceType) }
                    .onFailure { e ->
                        logger.error(e) { "⚠️ Failed to process message: id=${it.fileId}, topic=$topic" }
                    }
                    .getOrNull()
            }
        }.filterNotNull()

        logger.info {
            "✅ Batch complete: topic=$topic, total=${messages.size}, " +
                    "successful=${results.size}, failed=${messages.size - results.size}"
        }

        results
    }

    /**
     * 단일 메시지 정제 처리
     * 
     * 변경사항:
     * 1. 풀텍스트 추출 후 MongoDB에 저장
     * 2. Kafka에는 ID와 메타데이터만 전송
     */
    private suspend fun processMessage(
        message: InputMessage,
        sourceType: SourceType
    ): RefinedData = withContext(dispatcher) {
        val startTime = System.currentTimeMillis()

        // 1️⃣ MongoDB에서 원본 데이터 조회
        val originDocument = originDataRepository.findDataById(message.fileId)
            ?: error("Origin data not found: id=${message.fileId}")

        val mongoQueryTime = System.currentTimeMillis() - startTime
        logger.debug { "📄 Mongo query done: id=${message.fileId}, time=${mongoQueryTime}ms" }

        // 2️⃣ data 필드 추출
        val originData = originDocument["data"] as? Map<String, Any>
            ?: error("Invalid origin data format: id=${message.fileId}")

        // 3️⃣ ID 생성
        val refinedId = idGenerationService.generateId().toString()

        // 4️⃣ Full text 추출
        val extractionResult = fullTextExtractionService.extract(originData, sourceType)

        // 5️⃣ 메타데이터 생성
        val metadata = metadataGenerationService.generate(
            inputMessage = message,
            extractionSuccess = extractionResult.success,
            extractionTimeMs = extractionResult.durationMs,
            fullTextLength = extractionResult.text.length,
            errorMessage = extractionResult.error
        )

        // 6️⃣ 풀텍스트 데이터를 MongoDB에 저장 (Spring Data MongoDB 사용)
        val fullTextDocument = FullTextDocument(
            refinedId = refinedId,
            originId = message.fileId,
            sourceType = sourceType.name,
            fullText = extractionResult.text,
            originalData = extractMainFields(originData, sourceType),
            extractionSuccess = extractionResult.success,
            extractionTimeMs = extractionResult.durationMs,
            fullTextLength = extractionResult.text.length,
            errorMessage = extractionResult.error
        )
        
        val saveSuccess = runCatching {
            fullTextDataRepository.save(fullTextDocument)
            true
        }.onFailure { e ->
            logger.warn(e) { "⚠️ Failed to save full_text_data to MongoDB: refinedId=$refinedId" }
        }.getOrDefault(false)

        val totalTime = System.currentTimeMillis() - startTime
        logger.debug {
            "🔧 Processed: id=${message.fileId}, refinedId=$refinedId, " +
                    "type=${sourceType.name}, mongo=${mongoQueryTime}ms, " +
                    "extract=${extractionResult.durationMs}ms, total=${totalTime}ms, " +
                    "saved_to_mongo=$saveSuccess"
        }

        // 7️⃣ 경량화된 데이터만 반환 (Kafka로 전송됨)
        RefinedData(
            refinedId = refinedId,
            metadata = metadata
        )
    }

    /**
     * 원본 데이터에서 주요 필드만 추출
     * MongoDB full_text_data 컬렉션에 저장할 필드들
     */
    private fun extractMainFields(
        data: Map<String, Any>,
        sourceType: SourceType
    ): Map<String, Any> {
        val commonFields = setOf("id", "title", "author", "source")
        val specificFields = when (sourceType) {
            SourceType.RSS -> setOf("pubDate", "link", "category")
            SourceType.WIKI -> setOf("timestamp", "contributor")
            SourceType.TWITTER -> setOf("createdAt", "userId", "retweetCount", "likeCount")
        }
        return data.filterKeys { it in commonFields + specificFields }
    }
}

/**
 * 확장 함수: 코루틴 병렬 map
 */
private suspend fun <T, R> Iterable<T>.parallelMap(
    block: suspend (T) -> R
): List<R> = coroutineScope {
    map { async { block(it) } }.awaitAll()
}
