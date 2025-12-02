package com.javacafe.rtwindex.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.BulkResponse
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwindex.config.IndexProperties
import com.javacafe.rtwindex.model.IndexDocument
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.StringReader

private val logger = KotlinLogging.logger {}

/**
 * Elasticsearch 색인 서비스
 * 
 * Bulk API를 사용하여 대량의 문서를 효율적으로 색인
 * 
 * 주요 기능:
 * - 단일/배치 문서 색인
 * - 인덱스 자동 생성 (매핑 적용)
 * - 에러 핸들링 및 재시도
 */
@Service
class IndexService(
    private val elasticsearchClient: ElasticsearchClient,
    private val indexProperties: IndexProperties,
    @Qualifier("elasticsearchObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val articleIndexName: String
        get() = indexProperties.elasticsearch.articleIndex.name

    private val keywordIndexName: String
        get() = indexProperties.elasticsearch.keywordIndex.name

    /**
     * 애플리케이션 시작 시 인덱스 확인/생성
     */
    @PostConstruct
    fun initialize() {
        try {
            ensureIndexExists(articleIndexName, "elasticsearch/article-index-settings.json")
            ensureIndexExists(keywordIndexName, "elasticsearch/keyword-index-settings.json")
            logger.info { "✅ Elasticsearch indices initialized" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to initialize Elasticsearch indices" }
        }
    }

    /**
     * 인덱스 존재 확인 및 생성
     */
    private fun ensureIndexExists(indexName: String, settingsPath: String) {
        val exists = elasticsearchClient.indices()
            .exists(ExistsRequest.Builder().index(indexName).build())
            .value()

        if (exists) {
            logger.info { "Index '$indexName' already exists" }
            return
        }

        logger.info { "Creating index '$indexName' with settings from $settingsPath" }

        try {
            val settingsJson = ClassPathResource(settingsPath).inputStream.bufferedReader().readText()
            
            val createRequest = CreateIndexRequest.Builder()
                .index(indexName)
                .withJson(StringReader(settingsJson))
                .build()

            val response = elasticsearchClient.indices().create(createRequest)
            
            if (response.acknowledged()) {
                logger.info { "✅ Index '$indexName' created successfully" }
            } else {
                logger.warn { "⚠️ Index '$indexName' creation not acknowledged" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create index '$indexName'" }
            throw e
        }
    }

    /**
     * 단일 문서 색인
     * 
     * @param document 색인할 문서
     * @return 성공 여부
     */
    fun index(document: IndexDocument): Boolean {
        return try {
            val response = elasticsearchClient.index { builder ->
                builder
                    .index(articleIndexName)
                    .id(document.refinedId)
                    .document(document)
            }

            logger.debug { "Indexed document: ${document.refinedId}, result: ${response.result()}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to index document: ${document.refinedId}" }
            false
        }
    }

    /**
     * Bulk 색인 (대량 문서 처리)
     * 
     * @param documents 색인할 문서 목록
     * @return 성공한 문서 수
     */
    fun bulkIndex(documents: List<IndexDocument>): Int {
        if (documents.isEmpty()) return 0

        val startTime = System.currentTimeMillis()

        return try {
            // BulkOperation 목록 생성
            val operations = documents.map { doc ->
                BulkOperation.Builder()
                    .index(
                        IndexOperation.Builder<IndexDocument>()
                            .index(articleIndexName)
                            .id(doc.refinedId)
                            .document(doc)
                            .build()
                    )
                    .build()
            }

            // Bulk 요청 실행
            val bulkRequest = BulkRequest.Builder()
                .operations(operations)
                .refresh(Refresh.False)  // 성능을 위해 즉시 refresh 하지 않음
                .build()

            val response: BulkResponse = elasticsearchClient.bulk(bulkRequest)

            // 결과 분석
            val duration = System.currentTimeMillis() - startTime
            val successCount = response.items().count { it.error() == null }
            val failedCount = response.items().count { it.error() != null }

            if (response.errors()) {
                // 실패한 항목 로깅
                response.items()
                    .filter { it.error() != null }
                    .take(5)  // 최대 5개만 로깅
                    .forEach { item ->
                        logger.warn { 
                            "Bulk index failed for ${item.id()}: ${item.error()?.reason()}" 
                        }
                    }
                
                logger.warn { 
                    "Bulk index partial failure: success=$successCount, failed=$failedCount, " +
                    "duration=${duration}ms"
                }
            } else {
                logger.info { 
                    "Bulk index success: count=$successCount, duration=${duration}ms, " +
                    "avg=${duration / documents.size}ms/doc"
                }
            }

            successCount
        } catch (e: Exception) {
            logger.error(e) { "Bulk index failed: size=${documents.size}" }
            0
        }
    }

    /**
     * 청크 단위 Bulk 색인 (메모리 효율성)
     * 
     * @param documents 색인할 문서 목록
     * @param chunkSize 청크 크기 (기본 500)
     * @return 성공한 문서 수
     */
    fun bulkIndexChunked(documents: List<IndexDocument>, chunkSize: Int = 500): Int {
        if (documents.isEmpty()) return 0

        val startTime = System.currentTimeMillis()
        var totalSuccess = 0

        documents.chunked(chunkSize).forEachIndexed { index, chunk ->
            val success = bulkIndex(chunk)
            totalSuccess += success
            
            logger.debug { "Chunk ${index + 1}: indexed $success/${chunk.size} documents" }
        }

        val duration = System.currentTimeMillis() - startTime
        logger.info { 
            "Chunked bulk index complete: total=$totalSuccess/${documents.size}, " +
            "chunks=${(documents.size + chunkSize - 1) / chunkSize}, duration=${duration}ms"
        }

        return totalSuccess
    }

    /**
     * 문서 삭제
     * 
     * @param refinedId 삭제할 문서 ID
     * @return 성공 여부
     */
    fun delete(refinedId: String): Boolean {
        return try {
            val response = elasticsearchClient.delete { builder ->
                builder
                    .index(articleIndexName)
                    .id(refinedId)
            }

            logger.debug { "Deleted document: $refinedId, result: ${response.result()}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete document: $refinedId" }
            false
        }
    }

    /**
     * 문서 존재 여부 확인
     */
    fun exists(refinedId: String): Boolean {
        return try {
            elasticsearchClient.exists { builder ->
                builder
                    .index(articleIndexName)
                    .id(refinedId)
            }.value()
        } catch (e: Exception) {
            logger.error(e) { "Failed to check document existence: $refinedId" }
            false
        }
    }

    /**
     * 인덱스 문서 수 조회
     */
    fun getDocumentCount(indexName: String = articleIndexName): Long {
        return try {
            elasticsearchClient.count { builder ->
                builder.index(indexName)
            }.count()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get document count for index: $indexName" }
            0L
        }
    }

    /**
     * 인덱스 Refresh (검색 가능하도록)
     */
    fun refreshIndex(indexName: String = articleIndexName) {
        try {
            elasticsearchClient.indices().refresh { builder ->
                builder.index(indexName)
            }
            logger.debug { "Index refreshed: $indexName" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to refresh index: $indexName" }
        }
    }
}
