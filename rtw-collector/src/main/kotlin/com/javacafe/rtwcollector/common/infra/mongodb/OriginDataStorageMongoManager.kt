package com.javacafe.rtwcollector.common.infra.mongodb

import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwcollector.common.infra.OriginDataStorageManager
import com.javacafe.rtwcollector.common.infra.StorageWriteInfo
import com.javacafe.rtwcore.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*

@Component
@Primary
@ConditionalOnProperty(
    prefix = "storage",
    name = ["type"],
    havingValue = "mongodb"
)
class OriginDataStorageMongoManager(
    private val originDataRepository: OriginDataRepository,
    private val objectMapper: ObjectMapper
) : OriginDataStorageManager {

    companion object {
        private val logger = KotlinLogging.logger { }
        private const val COLLECTION_NAME = "origin_data"
        private const val MAX_PARALLEL_SAVES = 10
    }

    @Deprecated("Use saveSingleOriginData for individual items")
    override suspend fun saveParsedOriginData(
        originData: Any,
        prefix: String
    ): Result<StorageWriteInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val documentId = "${prefix}_${DateTimeUtils.localDateTimeNowAsStr()}_${UUID.randomUUID()}"
            val jsonString = objectMapper.writeValueAsString(originData)
            val dataSize = jsonString.toByteArray(Charsets.UTF_8).size.toLong()
            val checksum = calculateChecksum(jsonString)

            val document = OriginDataDocument(
                id = documentId,
                prefix = prefix,
                source = extractSourceFromOriginData(originData, prefix),
                data = originData,
                dataSize = dataSize,
                checksum = checksum,
                createdAt = LocalDateTime.now(),
                metadata = extractMetadataFromOriginData(originData)
            )

            val savedDocument = originDataRepository.save(document)

            logger.info {
                "Saved origin data (chunk) to MongoDB: ${savedDocument.id} " +
                        "(prefix: $prefix, size: ${dataSize} bytes)"
            }

            StorageWriteInfo(
                id = savedDocument.id,
                storageLocation = COLLECTION_NAME,
                dataSize = dataSize,
                checksum = checksum,
                metadata = mapOf(
                    "storageType" to "mongodb",
                    "storageMode" to "chunk",
                    "prefix" to prefix,
                    "source" to document.source
                )
            )
        }.onFailure { exception ->
            logger.error(exception) {
                "Failed to save origin data (chunk) to MongoDB (prefix: $prefix)"
            }
        }
    }

    override suspend fun saveSingleOriginData(
        originData: Any,
        prefix: String,
        metadata: Map<String, Any>
    ): Result<StorageWriteInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val documentId = "${prefix}_${DateTimeUtils.localDateTimeNowAsStr()}_${UUID.randomUUID()}"
            val jsonString = objectMapper.writeValueAsString(originData)
            val dataSize = jsonString.toByteArray(Charsets.UTF_8).size.toLong()
            val checksum = calculateChecksum(jsonString)
            val autoMetadata = extractMetadataFromOriginData(originData)
            val combinedMetadata = autoMetadata + metadata

            val document = OriginDataDocument(
                id = documentId,
                prefix = prefix,
                source = extractSourceFromOriginData(originData, prefix),
                data = originData,
                dataSize = dataSize,
                checksum = checksum,
                createdAt = LocalDateTime.now(),
                metadata = combinedMetadata
            )

            val savedDocument = originDataRepository.save(document)

            logger.debug {
                "Saved single origin data to MongoDB: ${savedDocument.id} " +
                        "(prefix: $prefix, size: ${dataSize} bytes)"
            }

            StorageWriteInfo(
                id = savedDocument.id,
                storageLocation = COLLECTION_NAME,
                dataSize = dataSize,
                checksum = checksum,
                metadata = mapOf(
                    "storageType" to "mongodb",
                    "storageMode" to "single",
                    "prefix" to prefix,
                    "source" to document.source
                )
            )
        }.onFailure { exception ->
            logger.error(exception) {
                "Failed to save single origin data to MongoDB (prefix: $prefix)"
            }
        }
    }

    override suspend fun saveBatchOriginData(
        originDataList: List<Any>,
        prefix: String,
        metadata: Map<String, Any>
    ): Result<List<StorageWriteInfo>> = runCatching {
        if (originDataList.isEmpty()) {
            logger.warn { "Empty origin data list provided for batch save" }
            return@runCatching emptyList<StorageWriteInfo>()
        }

        logger.info {
            "Starting batch save: ${originDataList.size} items (prefix: $prefix) " +
                    "with max $MAX_PARALLEL_SAVES parallel operations"
        }

        // coroutineScope 사용하여 병렬 처리
        val results = coroutineScope {
            originDataList
                .chunked(MAX_PARALLEL_SAVES)
                .flatMap { chunk ->
                    chunk.map { item ->
                        async(Dispatchers.IO) {
                            saveSingleOriginData(item, prefix, metadata)
                        }
                    }.awaitAll()
                        .mapNotNull { result ->
                            result.getOrNull()
                        }
                }
        }

        val successCount = results.size
        val failCount = originDataList.size - successCount

        logger.info {
            "Batch save completed: $successCount succeeded, $failCount failed " +
                    "(prefix: $prefix, total: ${originDataList.size})"
        }

        results
    }.onFailure { exception ->
        logger.error(exception) {
            "Failed to save batch origin data to MongoDB (prefix: $prefix)"
        }
    }

    override suspend fun retrieveOriginData(id: String): Result<Any?> = withContext(Dispatchers.IO) {
        runCatching {
            val document = originDataRepository.findById(id)
                .orElseThrow { NoSuchElementException("Document not found: $id") }
            document.data
        }.onFailure { exception ->
            logger.error(exception) { "Failed to retrieve origin data from MongoDB: $id" }
        }
    }

    private fun calculateChecksum(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun extractSourceFromOriginData(originData: Any, prefix: String): String {
        return when (prefix) {
            "twitter" -> "twitter_api"
            "rss" -> "rss_feed"
            "wiki" -> "wiki_file"
            else -> "unknown"
        }
    }

    private fun extractMetadataFromOriginData(originData: Any): Map<String, Any> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val dataMap = objectMapper.convertValue(originData, Map::class.java) as Map<String, Any>
            dataMap.filterKeys {
                it in listOf("id", "text", "title", "link", "pubDate", "author", "category")
            }
        } catch (e: Exception) {
            logger.debug { "Failed to extract metadata from origin data: ${e.message}" }
            emptyMap()
        }
    }
}
