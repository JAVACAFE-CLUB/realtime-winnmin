package com.javacafe.rtwindex.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

private val logger = KotlinLogging.logger {}

/**
 * Elasticsearch 클라이언트 설정
 * 
 * Elasticsearch Java API Client (co.elastic.clients) 사용
 * - ES 8.x 호환
 * - 타입 안전한 쿼리 빌더
 * - Bulk API 지원
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = ["com.javacafe.rtwindex.repository"])
class ElasticsearchConfig {

    @Value("\${spring.elasticsearch.uris:http://localhost:9200}")
    private lateinit var elasticsearchUris: String

    @Value("\${spring.elasticsearch.connection-timeout:5s}")
    private lateinit var connectionTimeout: String

    @Value("\${spring.elasticsearch.socket-timeout:30s}")
    private lateinit var socketTimeout: String

    /**
     * Low-level REST Client
     */
    @Bean(destroyMethod = "close")
    fun restClient(): RestClient {
        val hosts = elasticsearchUris.split(",").map { uri ->
            val url = java.net.URI.create(uri.trim())
            HttpHost(url.host, url.port, url.scheme)
        }.toTypedArray()

        logger.info { "Elasticsearch hosts: ${hosts.map { "${it.schemeName}://${it.hostName}:${it.port}" }}" }

        return RestClient.builder(*hosts)
            .setRequestConfigCallback { builder ->
                builder
                    .setConnectTimeout(parseDuration(connectionTimeout))
                    .setSocketTimeout(parseDuration(socketTimeout))
            }
            .build()
    }

    /**
     * Jackson ObjectMapper (ES JSON 직렬화용)
     */
    @Bean("elasticsearchObjectMapper")
    fun elasticsearchObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    /**
     * Elasticsearch Java API Client
     */
    @Bean
    fun elasticsearchClient(restClient: RestClient): ElasticsearchClient {
        val objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        
        val transport = RestClientTransport(restClient, JacksonJsonpMapper(objectMapper))
        
        logger.info { "Elasticsearch Java API Client initialized" }
        
        return ElasticsearchClient(transport)
    }

    /**
     * Duration 문자열 파싱 (예: "5s" -> 5000ms)
     */
    private fun parseDuration(duration: String): Int {
        val value = duration.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 5
        return when {
            duration.contains("ms") -> value
            duration.contains("s") -> value * 1000
            duration.contains("m") -> value * 60 * 1000
            else -> value * 1000
        }
    }
}
