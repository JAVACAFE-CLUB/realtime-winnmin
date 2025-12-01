package com.javacafe.rtwserve.application.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.JsonData
import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwcore.model.es.ArticleDocument
import com.javacafe.rtwcore.model.es.DailyKeyword
import com.javacafe.rtwcore.model.es.TopKeywordsResponse
import com.javacafe.rtwserve.application.dto.*
import com.javacafe.rtwserve.config.ServeProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * 키워드 조회 서비스
 * 
 * Redis 캐시와 Elasticsearch에서 키워드 데이터 조회
 */
@Service
class KeywordQueryService(
    private val elasticsearchClient: ElasticsearchClient,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val serveProperties: ServeProperties
) {
    private val articleIndex: String
        get() = serveProperties.elasticsearch.articleIndex
    
    private val keywordIndex: String
        get() = serveProperties.elasticsearch.keywordIndex
    
    private val cachePrefix: String
        get() = serveProperties.keyword.cachePrefix
    
    private val topCount: Int
        get() = serveProperties.keyword.topCount

    /**
     * 오늘의 키워드 조회 (캐시 우선)
     */
    fun getTodayKeywords(date: LocalDate = LocalDate.now()): TodayKeywordsResponse {
        // 1. Redis 캐시에서 조회
        val cached = getFromCache(date)
        if (cached != null) {
            logger.debug { "Cache hit for keywords: $date" }
            return TodayKeywordsResponse(
                date = cached.date,
                keywords = cached.keywords.map { it.toDto() },
                generatedAt = cached.generatedAt,
                cached = true
            )
        }

        // 2. 캐시 미스 - ES에서 조회
        logger.debug { "Cache miss for keywords: $date, fetching from ES" }
        val keywords = getKeywordsFromEs(date)
        
        return TodayKeywordsResponse(
            date = date,
            keywords = keywords.map { it.toDto() },
            generatedAt = LocalDateTime.now(),
            cached = false
        )
    }

    /**
     * 시간별 키워드 조회
     */
    fun getHourlyKeywords(dateTime: LocalDateTime = LocalDateTime.now()): TodayKeywordsResponse {
        val date = dateTime.toLocalDate()
        val hour = dateTime.hour

        val keywords = try {
            val searchRequest = SearchRequest.Builder()
                .index(keywordIndex)
                .size(topCount)
                .query { q ->
                    q.bool { b ->
                        b.filter { f -> f.term { t -> t.field("date").value(date.toString()) } }
                        b.filter { f -> f.term { t -> t.field("hour").value(hour.toLong()) } }
                    }
                }
                .sort { s -> s.field { f -> f.field("rank").order(SortOrder.Asc) } }
                .build()

            val response = elasticsearchClient.search(searchRequest, DailyKeyword::class.java)
            response.hits().hits().mapNotNull { it.source() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get hourly keywords" }
            emptyList()
        }

        return TodayKeywordsResponse(
            date = date,
            keywords = keywords.map { it.toDto() },
            generatedAt = LocalDateTime.now(),
            cached = false
        )
    }

    /**
     * 키워드 트렌드 조회 (최근 N일)
     */
    fun getKeywordTrend(keyword: String, days: Int = 7): KeywordTrendResponse {
        val today = LocalDate.now()
        val fromDate = today.minusDays(days.toLong() - 1)

        val trend = try {
            val searchRequest = SearchRequest.Builder()
                .index(keywordIndex)
                .size(days)
                .query { q ->
                    q.bool { b ->
                        b.filter { f -> f.term { t -> t.field("keyword").value(keyword) } }
                        b.filter { f -> f.range { r ->
                            r.field("date")
                                .gte(JsonData.of(fromDate.toString()))
                                .lte(JsonData.of(today.toString()))
                        } }
                        b.mustNot { mn -> mn.exists { e -> e.field("hour") } }  // 일별만 (시간별 제외)
                    }
                }
                .sort { s -> s.field { f -> f.field("date").order(SortOrder.Asc) } }
                .build()

            val response = elasticsearchClient.search(searchRequest, DailyKeyword::class.java)
            response.hits().hits().mapNotNull { hit ->
                hit.source()?.let { kw ->
                    DailyFrequency(
                        date = kw.date,
                        frequency = kw.frequency,
                        rank = kw.rank
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get keyword trend: $keyword" }
            emptyList()
        }

        return KeywordTrendResponse(
            keyword = keyword,
            trend = trend
        )
    }

    /**
     * 특정 키워드 관련 문서 조회
     */
    fun getArticlesByKeyword(
        keyword: String,
        page: Int = 0,
        size: Int = 20
    ): KeywordArticlesResponse {
        try {
            val searchRequest = SearchRequest.Builder()
                .index(articleIndex)
                .from(page * size)
                .size(size)
                .query { q ->
                    q.term { t -> t.field("keywords").value(keyword) }
                }
                .sort { s -> s.field { f -> f.field("indexedAt").order(SortOrder.Desc) } }
                .build()

            val response = elasticsearchClient.search(searchRequest, ArticleDocument::class.java)
            val totalHits = response.hits().total()?.value() ?: 0

            val articles = response.hits().hits().mapNotNull { hit ->
                hit.source()?.let { doc ->
                    ArticleDto(
                        id = doc.refinedId ?: "",
                        title = doc.title ?: "",
                        content = doc.fullText?.take(200) ?: "",
                        author = doc.author ?: "",
                        source = doc.source ?: "",
                        sourceType = doc.sourceType ?: "",
                        category = doc.category ?: "",
                        url = doc.url,
                        publishedAt = doc.publishedAt,
                        indexedAt = doc.indexedAt,
                        keywords = doc.keywords,
                        score = null
                    )
                }
            }

            return KeywordArticlesResponse(
                keyword = keyword,
                totalHits = totalHits,
                articles = articles
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get articles by keyword: $keyword" }
            return KeywordArticlesResponse(keyword, 0, emptyList())
        }
    }

    /**
     * Redis 캐시에서 조회
     */
    private fun getFromCache(date: LocalDate): TopKeywordsResponse? {
        val key = "${cachePrefix}${date}"
        return try {
            val value = redisTemplate.opsForValue().get(key)
            if (value != null) {
                objectMapper.convertValue(value, TopKeywordsResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get from cache: $key" }
            null
        }
    }

    /**
     * ES에서 키워드 조회
     */
    private fun getKeywordsFromEs(date: LocalDate): List<DailyKeyword> {
        return try {
            val searchRequest = SearchRequest.Builder()
                .index(keywordIndex)
                .size(topCount)
                .query { q ->
                    q.bool { b ->
                        b.filter { f -> f.term { t -> t.field("date").value(date.toString()) } }
                        b.mustNot { mn -> mn.exists { e -> e.field("hour") } }  // 일별만
                    }
                }
                .sort { s -> s.field { f -> f.field("rank").order(SortOrder.Asc) } }
                .build()

            val response = elasticsearchClient.search(searchRequest, DailyKeyword::class.java)
            response.hits().hits().mapNotNull { it.source() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get keywords from ES: $date" }
            emptyList()
        }
    }

    /**
     * DailyKeyword → KeywordDto 변환
     */
    private fun DailyKeyword.toDto(): KeywordDto {
        return KeywordDto(
            keyword = this.keyword,
            rank = this.rank,
            frequency = this.frequency,
            documentCount = this.documentCount,
            score = this.score,
            sourceBreakdown = this.sourceBreakdown,
            hourlyTrend = this.hourlyTrend,
            previousRank = this.previousRank,
            rankChange = this.rankChange,
            isNew = this.isNew
        )
    }
}
