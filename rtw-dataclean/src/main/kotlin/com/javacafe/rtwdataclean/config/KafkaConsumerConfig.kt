package com.javacafe.rtwdataclean.config

import com.javacafe.rtwdataclean.model.InputMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.JsonDeserializer


/**
 * Kafka Consumer 설정
 * rtw-core의 KafkaConfig는 Producer만 있으므로 Consumer는 여기서 설정
 */
@Configuration
@EnableKafka
class KafkaConsumerConfig {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.application.name}")
    private lateinit var groupId: String

    @Value("\${app.kafka.consumer.max-poll-records:100}")
    private var maxPollRecords: Int = 100

    @Value("\${app.kafka.consumer.max-poll-interval-ms:60000}")
    private var maxPollIntervalMs: Int = 60000

    @Value("\${app.kafka.consumer.session-timeout-ms:45000}")
    private var sessionTimeoutMs: Int = 45000

    @Value("\${app.kafka.consumer.heartbeat-interval-ms:15000}")
    private var heartbeatIntervalMs: Int = 15000

    @Bean
    fun consumerFactory(): ConsumerFactory<String, InputMessage> {
        // JsonDeserializer 설정
        val jsonDeserializer = JsonDeserializer(InputMessage::class.java).apply {
            addTrustedPackages("*")
            setUseTypeHeaders(false)  // 타입 헤더 무시
            setRemoveTypeHeaders(false)
        }
        
        val baseProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords,
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to maxPollIntervalMs,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to sessionTimeoutMs,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to heartbeatIntervalMs,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG to 
                "org.apache.kafka.clients.consumer.StickyAssignor"
        )
        
        // Static group membership (Kubernetes 환경에서만 적용)
        val hostname = System.getenv("HOSTNAME")
        val props = if (hostname != null) {
            baseProps + (ConsumerConfig.GROUP_INSTANCE_ID_CONFIG to hostname)
        } else {
            logger.warn { "HOSTNAME not found, static membership disabled" }
            baseProps
        }
        
        logger.info { 
            "Kafka Consumer Config: groupId=$groupId, maxPollRecords=$maxPollRecords, " +
            "maxPollIntervalMs=${maxPollIntervalMs}ms, staticMembership=${hostname != null}" 
        }
        
        // ErrorHandlingDeserializer를 사용하여 역직렬화 오류 처리
        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            jsonDeserializer
        )
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, InputMessage> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, InputMessage>()
        factory.consumerFactory = consumerFactory()
        
        // 수동 커밋
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        
        // Pod당 1개 컨슈머
        factory.setConcurrency(1)
        
        // 배치 리스너
        factory.isBatchListener = true
        
        return factory
    }
}
