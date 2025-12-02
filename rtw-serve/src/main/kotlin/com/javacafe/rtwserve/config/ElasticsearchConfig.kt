package com.javacafe.rtwserve.config

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

/**
 * Elasticsearch 설정
 */
@Configuration
@EnableElasticsearchRepositories(
    basePackages = ["com.javacafe.rtwserve.domain.*.repository"]
)
class ElasticsearchConfig {

    @Value("\${spring.elasticsearch.uris:http://localhost:9200}")
    private lateinit var elasticsearchUri: String

    /**
     * Elasticsearch REST Client
     */
    @Bean
    fun restClient(): RestClient {
        val uri = java.net.URI(elasticsearchUri)
        return RestClient.builder(
            HttpHost(uri.host, uri.port, uri.scheme)
        ).build()
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
        
        val transport = RestClientTransport(
            restClient,
            JacksonJsonpMapper(objectMapper)
        )
        
        return ElasticsearchClient(transport)
    }
}
