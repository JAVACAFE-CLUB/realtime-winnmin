package com.javacafe.rtwdataclean.service

import com.javacafe.rtwdataclean.model.ExtractionStatus
import com.javacafe.rtwdataclean.model.InputMessage
import com.javacafe.rtwdataclean.model.Metadata
import org.springframework.stereotype.Service

@Service
class MetadataGenerationService {

    fun generate(
        inputMessage: InputMessage,
        extractionSuccess: Boolean,
        extractionTimeMs: Long?,
        fullTextLength: Int,
        errorMessage: String?
    ): Metadata {
        val status = when {
            extractionSuccess -> ExtractionStatus.SUCCESS
            errorMessage?.contains("timeout", ignoreCase = true) == true -> ExtractionStatus.PARTIAL_SUCCESS
            else -> ExtractionStatus.FAILED
        }
        
        return Metadata(
            sourceId = inputMessage.fileId,
            source = inputMessage.source,
            originalChecksum = inputMessage.fileChecksum,
            fileSize = inputMessage.fileSize,
            extractionStatus = status,
            extractionTimeMs = extractionTimeMs,
            fullTextLength = fullTextLength,
            errorMessage = errorMessage
        )
    }
}
