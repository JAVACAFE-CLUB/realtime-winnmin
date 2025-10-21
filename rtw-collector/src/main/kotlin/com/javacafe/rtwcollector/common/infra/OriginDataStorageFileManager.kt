package com.javacafe.rtwcollector.common.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwcore.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories

@Component
@ConditionalOnProperty(
    prefix = "storage",
    name = ["type"],
    havingValue = "file",
    matchIfMissing = true
)
class OriginDataStorageFileManager(
    private val objectMapper: ObjectMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : OriginDataStorageManager {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Value("\${storage.file.directory:/Users/usmin/RtwOriginData}")
    private lateinit var originDataStoragePath: String

    private val outputPath by lazy {
        Path.of(originDataStoragePath).also { it.createDirectories() }
    }

    /**
     * 기존 청크 저장 방식 (하위 호환성)
     */
    @Deprecated("Use saveSingleOriginData for individual items")
    override suspend fun saveParsedOriginData(
        originData: Any,
        prefix: String
    ): Result<StorageWriteInfo> = withContext(ioDispatcher) {
        runCatching {
            val fileId = "${prefix}_${DateTimeUtils.localDateTimeNowAsStr()}_${UUID.randomUUID()}"
            val filePath = outputPath.resolve("$fileId.json")

            filePath.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(writer, originData)
            }
            logger.info { "Saved origin data (chunk) to file: $fileId.json" }

            createStorageWriteInfo(fileId, filePath, "chunk")
        }.onFailure { exception ->
            logger.error(exception) { "Failed to save origin data (chunk) to file" }
        }
    }

    /**
     * 단일 원본 데이터 저장 (신규)
     */
    override suspend fun saveSingleOriginData(
        originData: Any,
        prefix: String,
        metadata: Map<String, Any>
    ): Result<StorageWriteInfo> = withContext(ioDispatcher) {
        runCatching {
            val fileId = "${prefix}_${DateTimeUtils.localDateTimeNowAsStr()}_${UUID.randomUUID()}"
            val filePath = outputPath.resolve("$fileId.json")

            // 메타데이터를 포함한 JSON 작성
            val dataWithMetadata = mapOf(
                "data" to originData,
                "metadata" to metadata
            )

            filePath.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(writer, dataWithMetadata)
            }
            logger.debug { "Saved single origin data to file: $fileId.json" }

            createStorageWriteInfo(fileId, filePath, "single")
        }.onFailure { exception ->
            logger.error(exception) { "Failed to save single origin data to file" }
        }
    }

    /**
     * 배치 원본 데이터 저장 (신규)
     */
    override suspend fun saveBatchOriginData(
        originDataList: List<Any>,
        prefix: String,
        metadata: Map<String, Any>
    ): Result<List<StorageWriteInfo>> = withContext(ioDispatcher) {
        runCatching {
            if (originDataList.isEmpty()) {
                return@runCatching emptyList<StorageWriteInfo>()
            }

            logger.info {
                "Starting batch file save: ${originDataList.size} items (prefix: $prefix)"
            }

            val results = mutableListOf<StorageWriteInfo>()
            var successCount = 0

            originDataList.forEach { item ->
                saveSingleOriginData(item, prefix, metadata).fold(
                    onSuccess = { writeInfo ->
                        results.add(writeInfo)
                        successCount++
                    },
                    onFailure = { exception ->
                        logger.warn(exception) {
                            "Failed to save item in batch (continuing...)"
                        }
                    }
                )
            }

            logger.info {
                "Batch file save completed: $successCount/${originDataList.size} succeeded"
            }

            results
        }.onFailure { exception ->
            logger.error(exception) { "Failed to save batch origin data to file" }
        }
    }

    /**
     * 파일에서 조회
     */
    override suspend fun retrieveOriginData(id: String): Result<Any?> = withContext(ioDispatcher) {
        runCatching {
            val filePath = outputPath.resolve("$id.json")
            if (!Files.exists(filePath)) {
                throw NoSuchFileException(filePath.toFile(), reason = "파일이 존재하지 않습니다")
            }
            objectMapper.readValue(filePath.toFile(), Any::class.java)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to retrieve origin data from file: $id" }
        }
    }

    private fun calculateChecksum(filePath: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(filePath).use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createStorageWriteInfo(
        fileId: String,
        filePath: Path,
        storageMode: String
    ): StorageWriteInfo {
        val fileSize = Files.size(filePath)
        val checksum = calculateChecksum(filePath)

        return StorageWriteInfo(
            id = fileId,
            storageLocation = filePath.toString(),
            dataSize = fileSize,
            checksum = checksum,
            metadata = mapOf(
                "storageType" to "file",
                "storageMode" to storageMode
            )
        )
    }
}
