package com.javacafe.rtwcollector.common.infra

import kotlinx.coroutines.future.await
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class KafkaMessageProducer (
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    suspend fun sendCollectorCrawledMetadata(
        messageId: String,
        payload: Any,
        topic: String
    ): SendResult<String, Any> {
        val future: CompletableFuture<SendResult<String, Any>> =
            kafkaTemplate.send(topic, messageId, payload)

        return future.await()
    }
}
