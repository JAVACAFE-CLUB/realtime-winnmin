package com.javacafe.rtwdataclean.service

import com.javacafe.rtwdataclean.model.ContentType
import com.javacafe.rtwdataclean.model.SourceType
import info.bliki.wiki.filter.PlainTextConverter
import info.bliki.wiki.model.WikiModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withTimeout
import org.apache.tika.Tika
import org.apache.tika.exception.TikaException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

@Service
class FullTextExtractionService {
    private val tika = Tika()
    
    @Value("\${app.tika.max-text-length:1000000}")
    private var maxTextLength: Int = 1000000
    
    @Value("\${app.tika.timeout-ms:5000}")
    private var timeoutMs: Long = 5000

    /**
     * 토픽별로 데이터에서 Full Text 추출
     */
    suspend fun extract(data: Map<String, Any>, sourceType: SourceType): ExtractionResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 1. 지정된 경로에서 텍스트 추출
            val rawContent = extractFromPath(data, sourceType.textPath)
            
            if (rawContent == null) {
                logger.warn { 
                    "Text not found at path: ${sourceType.textPath.joinToString(" > ")}, " +
                    "sourceType: ${sourceType.name}" 
                }
                return ExtractionResult(
                    success = false,
                    text = "",
                    error = "Text not found at path: ${sourceType.textPath.joinToString(" > ")}",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
            
            // 2. 컨텐츠 타입에 따라 처리
            val extractedText = when (sourceType.contentType) {
                ContentType.HTML -> extractFromHtml(rawContent)
                ContentType.WIKI_MARKUP -> extractFromWikiMarkup(rawContent)
                ContentType.PLAIN_TEXT -> rawContent
            }
            
            // 3. 텍스트 길이 제한
            val truncatedText = if (extractedText.length > maxTextLength) {
                logger.warn { 
                    "Text too long, truncating: sourceType=${sourceType.name}, " +
                    "length=${extractedText.length}" 
                }
                extractedText.substring(0, maxTextLength) + "\n[TRUNCATED]"
            } else {
                extractedText
            }
            
            val duration = System.currentTimeMillis() - startTime
            logger.debug { 
                "Text extraction successful: sourceType=${sourceType.name}, " +
                "contentType=${sourceType.contentType}, textLength=${truncatedText.length}, " +
                "duration=${duration}ms" 
            }
            
            ExtractionResult(
                success = true,
                text = truncatedText.trim(),
                durationMs = duration
            )
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "Unexpected error during extraction: sourceType=${sourceType.name}" }
            ExtractionResult(
                success = false,
                text = "",
                error = "Unexpected error: ${e.message}",
                durationMs = duration
            )
        }
    }
    
    /**
     * JSON 경로를 따라 텍스트 추출
     * 예: ["data", "revision", "text"] → data.revision.text
     */
    private fun extractFromPath(data: Map<String, Any>, path: List<String>): String? {
        var current: Any? = data
        
        for (key in path) {
            current = when (current) {
                is Map<*, *> -> current[key]
                else -> null
            }
            
            if (current == null) {
                return null
            }
        }
        
        return current as? String
    }
    
    /**
     * Wikipedia 마크업에서 텍스트 추출 (Bliki 사용)
     */
    private suspend fun extractFromWikiMarkup(wikiMarkup: String): String {
        return try {
            withTimeout(timeoutMs.milliseconds) {
                // 1. Bliki를 사용하여 Wiki 마크업 → HTML 변환
                val wikiModel = WikiModel("", "")
                val plainText = wikiModel.render(PlainTextConverter(), wikiMarkup)
                
                logger.debug { "Wiki markup converted to plainText, length=${plainText.length}" }
                
                plainText
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error(e) { "Wiki markup extraction timeout: ${timeoutMs}ms" }
            throw IllegalStateException("Wiki markup extraction timeout after ${timeoutMs}ms", e)
        } catch (e: Exception) {
            logger.error(e) { "Bliki wiki markup extraction failed" }
            throw IllegalStateException("Bliki extraction failed: ${e.message}", e)
        }
    }
    
    /**
     * HTML에서 텍스트 추출 (Tika 사용)
     */
    private suspend fun extractFromHtml(htmlContent: String): String {
        return try {
            withTimeout(timeoutMs.milliseconds) {
                val htmlBytes = htmlContent.toByteArray(Charsets.UTF_8)
                tika.parseToString(ByteArrayInputStream(htmlBytes))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error(e) { "HTML extraction timeout: ${timeoutMs}ms" }
            throw IllegalStateException("HTML extraction timeout after ${timeoutMs}ms", e)
        } catch (e: TikaException) {
            logger.error(e) { "Tika HTML extraction failed" }
            throw IllegalStateException("Tika HTML extraction failed: ${e.message}", e)
        }
    }
    
    data class ExtractionResult(
        val success: Boolean,
        val text: String,
        val error: String? = null,
        val durationMs: Long
    )
}
