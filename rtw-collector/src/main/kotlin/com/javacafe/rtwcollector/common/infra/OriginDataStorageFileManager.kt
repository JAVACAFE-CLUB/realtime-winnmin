package com.javacafe.rtwcollector.common.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwcollector.common.utils.DateTimeUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories

@Component
class OriginDataStorageFileManager(
    private val objectMapper: ObjectMapper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Value("\${wiki.output.directory:/Users/usmin/RtwOriginData}")
    private lateinit var originDataStoragePath: String

    private val outputPath by lazy {
        Path.of(originDataStoragePath).also { it.createDirectories() }
    }

    suspend fun saveParsedOriginDataToFile(
        originData: Any,
        prefix: String
    ): Result<FileWriteInfo> = withContext(ioDispatcher) {
        runCatching {
            val fileId = "${prefix}_${DateTimeUtils.localDateTimeNowAsStr()}_${UUID.randomUUID()}"
            val filePath = outputPath.resolve("$fileId.json")

            filePath.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(writer, originData)
            }
            logger.info { "Saved origin data to file: $fileId.json" }

            createFileWriteInfo(fileId, filePath)
        }.onFailure { exception ->
            logger.error(exception) { "Failed to save origin data to file" }
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

    private fun createFileWriteInfo(fileId: String, filePath: Path): FileWriteInfo {
        val fileSize = Files.size(filePath)
        val checksum = calculateChecksum(filePath)

        return FileWriteInfo(
            fileId = fileId,
            path = filePath.toString(),
            fileSize = fileSize,
            checksum = checksum
        )
    }
}
