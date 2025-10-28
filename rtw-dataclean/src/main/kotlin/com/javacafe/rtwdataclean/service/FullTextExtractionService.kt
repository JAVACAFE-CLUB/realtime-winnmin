package com.javacafe.rtwdataclean.service

import com.javacafe.rtwdataclean.model.ContentType
import com.javacafe.rtwdataclean.model.SourceType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withTimeout
import org.apache.commons.text.StringEscapeUtils
import org.apache.tika.Tika
import org.apache.tika.exception.TikaException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

@Service
class FullTextExtractionService {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val tika = Tika()

    @Value("\${app.tika.max-text-length:1000000}")
    private var maxTextLength: Int = 1_000_000

    @Value("\${app.tika.timeout-ms:5000}")
    private var timeoutMs: Long = 5_000

    /**
     * 토픽별로 데이터에서 Full Text 추출
     */
    suspend fun extract(data: Map<String, Any>, sourceType: SourceType): ExtractionResult {
        var result: ExtractionResult
        val duration = measureTimeMillis {
            result = runCatching {
                val rawContent = extractFromPath(data, sourceType.textPath)
                    ?: error("Text not found at path: ${sourceType.textPath.joinToString(" > ")}")

                val extractedText = when (sourceType.contentType) {
                    ContentType.HTML -> extractFromHtml(rawContent)
                    ContentType.PLAIN_TEXT -> rawContent
                }

                // HTML 엔티티 디코딩
                val decoded = StringEscapeUtils.unescapeHtml4(extractedText)

                // 텍스트 길이 제한
                val finalText = decoded
                    .takeIf { it.length <= maxTextLength }
                    ?: (decoded.take(maxTextLength) + "\n[TRUNCATED]")

                ExtractionResult(
                    success = true,
                    text = finalText.trim()
                )
            }.getOrElse { e ->
                logger.error(e) { "Extraction failed: sourceType=${sourceType.name}" }
                ExtractionResult(
                    success = false,
                    text = "",
                    error = e.message
                )
            }
        }

        logger.debug {
            "Text extraction ${if (result.success) "OK" else "FAIL"}: " +
                    "sourceType=${sourceType.name}, " +
                    "contentType=${sourceType.contentType}, " +
                    "length=${result.text.length}, duration=${duration}ms"
        }

        return result.copy(durationMs = duration)
    }

    /**
     * JSON 경로를 따라 텍스트 추출
     */
    private fun extractFromPath(data: Map<String, Any>, path: List<String>): String? =
        path.fold(data as Any?) { current, key -> (current as? Map<*, *>)?.get(key) } as? String

    /**
     * HTML에서 텍스트 추출 (Tika + timeout)
     */
    private suspend fun extractFromHtml(htmlContent: String): String = try {
        withTimeout(timeoutMs.milliseconds) {
            ByteArrayInputStream(htmlContent.toByteArray(Charsets.UTF_8)).use {
                tika.parseToString(it)
            }
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw IllegalStateException("HTML extraction timeout after ${timeoutMs}ms", e)
    } catch (e: TikaException) {
        throw IllegalStateException("Tika HTML extraction failed: ${e.message}", e)
    }

    /**
     * 추출 결과
     */
    data class ExtractionResult(
        val success: Boolean,
        val text: String,
        val error: String? = null,
        val durationMs: Long = 0
    )
}
