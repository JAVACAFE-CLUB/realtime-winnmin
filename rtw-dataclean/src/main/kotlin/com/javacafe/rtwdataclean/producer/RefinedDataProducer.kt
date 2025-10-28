package com.javacafe.rtwdataclean.producer

import com.javacafe.rtwdataclean.model.RefinedData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.future.await
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component


@Component
class RefinedDataProducer(
    // rtw-core의 KafkaTemplate<String, Any> 사용
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Value("\${app.kafka.output-topic}")
    private lateinit var outputTopic: String

    /**
     * 정재된 데이터를 Kafka로 전송
     */
    suspend fun send(sourceType: String, refinedData: RefinedData): Boolean {
        return try {
            val result =
                when (sourceType) {
                    "rss-items" -> kafkaTemplate
                        .send("refined-rss", refinedData.refinedId, refinedData)
                        .await()
                    "wiki-items" -> kafkaTemplate
                        .send("refined-wiki", refinedData.refinedId, refinedData)
                        .await()
                    "twitter-items" -> kafkaTemplate
                        .send("refined-twitter", refinedData.refinedId, refinedData)
                        .await()
                    else -> kafkaTemplate
                        .send(outputTopic, refinedData.refinedId, refinedData)
                        .await()
                }
            
            logger.debug { 
                "Message sent: refinedId=${refinedData.refinedId}, " +
                "partition=${result.recordMetadata.partition()}, " +
                "offset=${result.recordMetadata.offset()}" 
            }
            
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to send message: refinedId=${refinedData.refinedId}" }
            false
        }
    }
    
    /**
     * 배치 전송
     */
    suspend fun sendBatch(sourceType: String, refinedDataList: List<RefinedData>): Int {
        var successCount = 0
        
        refinedDataList.forEach { refinedData ->
            if (send(sourceType = sourceType, refinedData = refinedData)) {
                successCount++
            }
        }
        
        logger.info { 
            "Batch sent: total=${refinedDataList.size}, success=$successCount, " +
            "failed=${refinedDataList.size - successCount}" 
        }
        
        return successCount
    }
}
