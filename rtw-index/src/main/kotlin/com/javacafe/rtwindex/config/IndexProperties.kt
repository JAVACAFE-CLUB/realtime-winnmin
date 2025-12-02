package com.javacafe.rtwindex.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * rtw-index 애플리케이션 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "app")
data class IndexProperties(
    val kafka: KafkaProperties,
    val elasticsearch: ElasticsearchProperties,
    val morpheme: MorphemeProperties,
    val keyword: KeywordProperties,
    val processing: ProcessingProperties
)

/**
 * Kafka Consumer 설정
 */
data class KafkaProperties(
    val inputTopics: List<String>,
    val consumer: ConsumerProperties
)

data class ConsumerProperties(
    val groupId: String,
    val maxPollRecords: Int = 500,
    val maxPollIntervalMs: Int = 300000,
    val sessionTimeoutMs: Int = 45000,
    val heartbeatIntervalMs: Int = 15000,
    val fetchMinBytes: Int = 50000,
    val fetchMaxWaitMs: Int = 500,
    val concurrency: Int = 3,
    val enableAutoCommit: Boolean = false,
    val autoOffsetReset: String = "earliest"
)

/**
 * Elasticsearch 인덱스 설정
 */
data class ElasticsearchProperties(
    val articleIndex: IndexConfig,
    val keywordIndex: IndexConfig,
    val bulk: BulkProperties
)

data class IndexConfig(
    val name: String,
    val shards: Int = 3,
    val replicas: Int = 1,
    val refreshInterval: String = "5s"
)

data class BulkProperties(
    val size: Int = 500,
    val flushInterval: String = "5s",
    val concurrentRequests: Int = 2
)

/**
 * 형태소 분석 설정
 */
data class MorphemeProperties(
    val analyzer: String = "korean_noun_only",
    val minKeywordLength: Int = 2,
    val maxKeywordLength: Int = 20,
    val maxKeywordsPerDoc: Int = 50
)

/**
 * 오늘의 키워드 설정
 */
data class KeywordProperties(
    val topCount: Int = 10,
    val cachePrefix: String = "rtw:keyword:daily:",
    val cacheTtl: Long = 86400,
    val aggregationCron: String = "0 0 * * * *"
)

/**
 * 처리 설정
 */
data class ProcessingProperties(
    val parallelism: Int = 30,
    val timeoutSeconds: Long = 60
)
