package com.javacafe.rtwdataclean.config

import com.javacafe.rtwdataclean.model.RefinedData
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer


@Configuration
class KafkaProducerConfig {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Bean
    fun refinedDataProducerFactory(): ProducerFactory<String, RefinedData> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "1",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.BATCH_SIZE_CONFIG to 32768,
            ProducerConfig.LINGER_MS_CONFIG to 20,
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy",
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        
        logger.info { "Kafka Producer Config initialized for RefinedData" }
        
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun refinedDataKafkaTemplate(): KafkaTemplate<String, RefinedData> {
        return KafkaTemplate(refinedDataProducerFactory())
    }
}
