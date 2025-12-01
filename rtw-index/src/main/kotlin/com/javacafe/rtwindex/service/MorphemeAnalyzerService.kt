package com.javacafe.rtwindex.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.indices.AnalyzeRequest
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse
import com.javacafe.rtwindex.config.IndexProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * 형태소 분석 서비스
 * 
 * Elasticsearch의 Nori 분석기를 활용하여 한국어 텍스트를 분석
 * 
 * 사용 가능한 분석기:
 * - korean_standard: 일반 색인용 (명사 위주)
 * - korean_noun_only: 명사만 추출 (키워드용)
 * - korean_search: 검색 쿼리용 (동의어 포함)
 */
@Service
class MorphemeAnalyzerService(
    private val elasticsearchClient: ElasticsearchClient,
    private val indexProperties: IndexProperties
) {
    private val indexName: String
        get() = indexProperties.elasticsearch.articleIndex.name

    private val defaultAnalyzer: String
        get() = indexProperties.morpheme.analyzer

    /**
     * 텍스트 분석 (기본 분석기 사용)
     * 
     * @param text 분석할 텍스트
     * @param analyzer 분석기 이름 (기본: korean_noun_only)
     * @return 토큰 목록
     */
    fun analyze(text: String, analyzer: String = defaultAnalyzer): List<AnalyzedToken> {
        if (text.isBlank()) return emptyList()

        return try {
            val request = AnalyzeRequest.Builder()
                .index(indexName)
                .analyzer(analyzer)
                .text(text)
                .build()

            val response: AnalyzeResponse = elasticsearchClient.indices().analyze(request)

            response.tokens().map { token ->
                AnalyzedToken(
                    token = token.token(),
                    startOffset = token.startOffset().let { if (it is Long) it.toInt() else it as Int },
                    endOffset = token.endOffset().let { if (it is Long) it.toInt() else it as Int },
                    position = token.position().let { if (it is Long) it.toInt() else it as Int },
                    type = token.type()
                )
            }.also {
                logger.debug { "Analyzed text (length=${text.length}): ${it.size} tokens extracted" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to analyze text with analyzer '$analyzer'" }
            emptyList()
        }
    }

    /**
     * 명사만 추출
     * 
     * korean_noun_only 분석기를 사용하여 명사만 필터링
     * 
     * @param text 분석할 텍스트
     * @return 명사 목록
     */
    fun extractNouns(text: String): List<String> {
        return analyze(text, "korean_noun_only").map { it.token }
    }

    /**
     * 표준 분석 (일반 색인용)
     * 
     * @param text 분석할 텍스트
     * @return 토큰 목록
     */
    fun analyzeStandard(text: String): List<String> {
        return analyze(text, "korean_standard").map { it.token }
    }

    /**
     * 검색 쿼리 분석 (동의어 확장)
     * 
     * @param text 검색 쿼리
     * @return 확장된 토큰 목록
     */
    fun analyzeForSearch(text: String): List<String> {
        return analyze(text, "korean_search").map { it.token }
    }

    /**
     * 토큰 빈도수 계산
     * 
     * @param tokens 토큰 목록
     * @return 토큰별 빈도수 맵 (빈도순 정렬)
     */
    fun countTokenFrequency(tokens: List<String>): Map<String, Int> {
        return tokens
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    }

    /**
     * 텍스트에서 상위 N개 토큰 추출
     * 
     * @param text 분석할 텍스트
     * @param topN 추출할 토큰 수
     * @param analyzer 분석기 이름
     * @return 상위 토큰 목록 (빈도순)
     */
    fun getTopTokens(text: String, topN: Int = 10, analyzer: String = defaultAnalyzer): List<TokenFrequency> {
        val tokens = analyze(text, analyzer).map { it.token }
        val frequency = countTokenFrequency(tokens)

        return frequency.entries
            .take(topN)
            .map { TokenFrequency(it.key, it.value) }
    }

    /**
     * 인덱스 분석기 설정 확인
     */
    fun getAnalyzerInfo(): Map<String, Any> {
        return try {
            val response = elasticsearchClient.indices()
                .getSettings { it.index(indexName) }

            val settings = response.result()[indexName]?.settings()
            
            mapOf(
                "index" to indexName,
                "analysisConfigured" to (settings?.index()?.analysis() != null),
                "availableAnalyzers" to listOf(
                    "korean_standard",
                    "korean_noun_only", 
                    "korean_search"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get analyzer info" }
            mapOf("error" to e.message.orEmpty())
        }
    }
}

/**
 * 분석된 토큰 정보
 */
data class AnalyzedToken(
    val token: String,
    val startOffset: Int,
    val endOffset: Int,
    val position: Int,
    val type: String
)

/**
 * 토큰 빈도수
 */
data class TokenFrequency(
    val token: String,
    val frequency: Int
)
