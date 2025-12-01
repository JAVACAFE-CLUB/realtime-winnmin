package com.javacafe.rtwindex.config

import com.javacafe.rtwindex.model.RefinedDataMessage
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

private val logger = KotlinLogging.logger {}

/**
 * Kafka Consumer 설정
 * 
 * 고성능 배치 처리를 위한 튜닝:
 * - 배치 리스너 (한 번에 여러 메시지 처리)
 * - 수동 커밋 (MANUAL_IMMEDIATE)
 * - 병렬 처리 (concurrency)
 * - fetch 최적화 (min.bytes, max.wait.ms)
 */
@Configuration
@EnableKafka
class KafkaConsumerConfig {

    @Value("\${kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${app.kafka.consumer.group-id}")
    private lateinit var groupId: String

    @Value("\${app.kafka.consumer.max-poll-records:500}")
    private var maxPollRecords: Int = 500

    @Value("\${app.kafka.consumer.max-poll-interval-ms:300000}")
    private var maxPollIntervalMs: Int = 300000

    @Value("\${app.kafka.consumer.session-timeout-ms:45000}")
    private var sessionTimeoutMs: Int = 45000

    @Value("\${app.kafka.consumer.heartbeat-interval-ms:15000}")
    private var heartbeatIntervalMs: Int = 15000

    @Value("\${app.kafka.consumer.fetch-min-bytes:50000}")
    private var fetchMinBytes: Int = 50000

    @Value("\${app.kafka.consumer.fetch-max-wait-ms:500}")
    private var fetchMaxWaitMs: Int = 500

    @Value("\${app.kafka.consumer.concurrency:3}")
    private var concurrency: Int = 3

    @Value("\${app.kafka.consumer.auto-offset-reset:earliest}")
    private var autoOffsetReset: String = "earliest"

    /**
     * Consumer Factory 생성
     * 
     * 고성능 처리를 위한 설정:
     * - max.poll.records: 배치 크기 (500)
     * - fetch.min.bytes: 최소 fetch 크기 (50KB) - 네트워크 효율성
     * - fetch.max.wait.ms: fetch 대기 시간 (500ms)
     * - StickyAssignor: 파티션 재할당 최소화
     */
    @Bean
    fun indexConsumerFactory(): ConsumerFactory<String, RefinedDataMessage> {
        val jsonDeserializer = JsonDeserializer(RefinedDataMessage::class.java).apply {
            addTrustedPackages("*")
            setUseTypeHeaders(false)
            setRemoveTypeHeaders(false)
        }

        val props = mutableMapOf<String, Any>(
            // 기본 연결 설정
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            
            // 배치 처리 설정
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords,
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to maxPollIntervalMs,
            
            // 세션 관리
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to sessionTimeoutMs,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to heartbeatIntervalMs,
            
            // Fetch 최적화 (배치 효율성 향상)
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to fetchMinBytes,
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to fetchMaxWaitMs,
            
            // 오프셋 관리
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
            
            // 파티션 할당 전략 (Sticky: 리밸런싱 최소화)
            ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG to
                "org.apache.kafka.clients.consumer.CooperativeStickyAssignor"
        )

        // Static Group Membership (Kubernetes 환경)
        val hostname = System.getenv("HOSTNAME")
        if (hostname != null) {
            props[ConsumerConfig.GROUP_INSTANCE_ID_CONFIG] = hostname
            logger.info { "Static group membership enabled: $hostname" }
        }

        logger.info { 
            """
            |Kafka Consumer Config:
            |  - groupId: $groupId
            |  - maxPollRecords: $maxPollRecords
            |  - fetchMinBytes: $fetchMinBytes
            |  - fetchMaxWaitMs: $fetchMaxWaitMs
            |  - concurrency: $concurrency
            |  - staticMembership: ${hostname != null}
            """.trimMargin()
        }

        return DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            jsonDeserializer
        )
    }

    /**
     * Kafka Listener Container Factory
     * 
     * 배치 처리 + 수동 커밋 설정
     */
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage> {
        return ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage>().apply {
            consumerFactory = indexConsumerFactory()
            
            // 배치 리스너 활성화
            isBatchListener = true
            
            // 수동 커밋 (배치 처리 완료 후)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
            
            // 동시 처리 컨슈머 수 (파티션 수에 맞춤)
            setConcurrency(concurrency)
            
            // 에러 발생 시 컨테이너 중지 방지
            containerProperties.isMissingTopicsFatal = false
            
            logger.info { "Kafka Listener Factory: batchListener=true, ackMode=MANUAL_IMMEDIATE, concurrency=$concurrency" }
        }
    }

    /**
     * RSS 전용 Consumer Factory (별도 그룹)
     */
    @Bean
    fun rssKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage> {
        return createTopicSpecificFactory("$groupId-rss", 3)
    }

    /**
     * Wiki 전용 Consumer Factory (별도 그룹)
     */
    @Bean
    fun wikiKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage> {
        return createTopicSpecificFactory("$groupId-wiki", 3)
    }

    /**
     * Twitter 전용 Consumer Factory (별도 그룹)
     */
    @Bean
    fun twitterKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage> {
        return createTopicSpecificFactory("$groupId-twitter", 3)
    }

    /**
     * 토픽별 전용 Factory 생성
     */
    private fun createTopicSpecificFactory(
        specificGroupId: String,
        specificConcurrency: Int
    ): ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage> {
        val jsonDeserializer = JsonDeserializer(RefinedDataMessage::class.java).apply {
            addTrustedPackages("*")
            setUseTypeHeaders(false)
            setRemoveTypeHeaders(false)
        }

        val props = mutableMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to specificGroupId,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to maxPollRecords,
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to maxPollIntervalMs,
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to sessionTimeoutMs,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to heartbeatIntervalMs,
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to fetchMinBytes,
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to fetchMaxWaitMs,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
            ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG to
                "org.apache.kafka.clients.consumer.CooperativeStickyAssignor"
        )

        val consumerFactory = DefaultKafkaConsumerFactory(
            props,
            StringDeserializer(),
            jsonDeserializer
        )

        return ConcurrentKafkaListenerContainerFactory<String, RefinedDataMessage>().apply {
            this.consumerFactory = consumerFactory
            isBatchListener = true
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
            setConcurrency(specificConcurrency)
            containerProperties.isMissingTopicsFatal = false
        }
    }
}
