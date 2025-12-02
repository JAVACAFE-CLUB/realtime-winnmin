package com.javacafe.rtwindex.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.json.JsonData
import com.javacafe.rtwcore.model.es.DailyKeyword
import com.javacafe.rtwcore.model.es.HourlyCount
import com.javacafe.rtwcore.model.es.SourceBreakdown
import com.javacafe.rtwindex.config.IndexProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * 키워드 집계 서비스
 * 
 * Elasticsearch Aggregation을 사용하여 키워드 통계 생성
 */
@Service
class KeywordAggregationService(
    private val elasticsearchClient: ElasticsearchClient,
    private val indexProperties: IndexProperties
) {
    private val articleIndexName: String
        get() = indexProperties.elasticsearch.articleIndex.name

    private val keywordIndexName: String
        get() = indexProperties.elasticsearch.keywordIndex.name

    private val topCount: Int
        get() = indexProperties.keyword.topCount

    /**
     * 날짜 범위 쿼리 생성
     */
    private fun createDateRangeQuery(startDate: String, endDate: String): Query {
        val rangeQuery = RangeQuery.Builder()
            .field("indexedAt")
            .gte(JsonData.of(startDate))
            .lt(JsonData.of(endDate))
            .build()
        
        return Query.Builder()
            .range(rangeQuery)
            .build()
    }

    /**
     * 키워드 Terms Aggregation 생성
     */
    private fun createKeywordAggregation(limit: Int, withSubAggs: Boolean = false): Aggregation {
        val termsAggBuilder = Aggregation.Builder()
            .terms { t -> t.field("keywords").size(limit * 2) }

        if (withSubAggs) {
            termsAggBuilder
                .aggregations("by_source", Aggregation.Builder()
                    .terms { t -> t.field("sourceType").size(10) }
                    .build())
                .aggregations("by_hour", Aggregation.Builder()
                    .dateHistogram { d -> d.field("indexedAt").calendarInterval(CalendarInterval.Hour) }
                    .build())
        }

        return termsAggBuilder.build()
    }

    /**
     * 오늘의 Top 키워드 집계
     */
    fun aggregateDailyKeywords(
        date: LocalDate = LocalDate.now(),
        limit: Int = topCount
    ): List<DailyKeyword> {
        val startTime = System.currentTimeMillis()
        
        try {
            val startOfDay = date.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val endOfDay = date.plusDays(1).atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            val searchRequest = SearchRequest.Builder()
                .index(articleIndexName)
                .size(0)
                .query(createDateRangeQuery(startOfDay, endOfDay))
                .aggregations("top_keywords", createKeywordAggregation(limit, true))
                .build()

            val response = elasticsearchClient.search(searchRequest, Void::class.java)

            val keywordBuckets = response.aggregations()["top_keywords"]
                ?.sterms()
                ?.buckets()
                ?.array() ?: emptyList()

            val previousDayKeywords = getPreviousDayRanks(date.minusDays(1))

            val dailyKeywords = keywordBuckets.mapIndexed { index, bucket ->
                val keyword = bucket.key().stringValue()
                val frequency = bucket.docCount()
                val rank = index + 1

                val sourceBreakdown = parseSourceBreakdown(bucket)
                val hourlyTrend = parseHourlyTrend(bucket)

                val previousRank = previousDayKeywords[keyword]
                val rankChange = previousRank?.let { it - rank }
                val isNew = previousRank == null

                DailyKeyword(
                    date = date,
                    keyword = keyword,
                    frequency = frequency,
                    documentCount = frequency.toInt(),
                    score = calculateScore(frequency, rank),
                    rank = rank,
                    sourceBreakdown = sourceBreakdown,
                    hourlyTrend = hourlyTrend,
                    previousRank = previousRank,
                    rankChange = rankChange,
                    isNew = isNew
                )
            }.take(limit)

            val duration = System.currentTimeMillis() - startTime
            logger.info { 
                "Daily keywords aggregated for $date: ${dailyKeywords.size} keywords, duration=${duration}ms" 
            }

            return dailyKeywords

        } catch (e: Exception) {
            logger.error(e) { "Failed to aggregate daily keywords for $date" }
            return emptyList()
        }
    }

    /**
     * 시간별 키워드 집계
     */
    fun aggregateHourlyKeywords(
        dateTime: LocalDateTime = LocalDateTime.now(),
        limit: Int = topCount
    ): List<DailyKeyword> {
        val startTime = System.currentTimeMillis()
        val hour = dateTime.hour
        val startOfHour = dateTime.withMinute(0).withSecond(0).withNano(0)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val endOfHour = dateTime.withMinute(0).withSecond(0).withNano(0)
            .plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

        try {
            val searchRequest = SearchRequest.Builder()
                .index(articleIndexName)
                .size(0)
                .query(createDateRangeQuery(startOfHour, endOfHour))
                .aggregations("top_keywords", createKeywordAggregation(limit, false))
                .build()

            val response = elasticsearchClient.search(searchRequest, Void::class.java)

            val keywordBuckets = response.aggregations()["top_keywords"]
                ?.sterms()
                ?.buckets()
                ?.array() ?: emptyList()

            val hourlyKeywords = keywordBuckets.mapIndexed { index, bucket ->
                DailyKeyword(
                    date = dateTime.toLocalDate(),
                    hour = hour,
                    keyword = bucket.key().stringValue(),
                    frequency = bucket.docCount(),
                    documentCount = bucket.docCount().toInt(),
                    score = calculateScore(bucket.docCount(), index + 1),
                    rank = index + 1
                )
            }.take(limit)

            val duration = System.currentTimeMillis() - startTime
            logger.debug { 
                "Hourly keywords aggregated for $startOfHour: ${hourlyKeywords.size} keywords, duration=${duration}ms" 
            }

            return hourlyKeywords

        } catch (e: Exception) {
            logger.error(e) { "Failed to aggregate hourly keywords for $dateTime" }
            return emptyList()
        }
    }

    /**
     * 집계 결과를 ES에 저장
     */
    fun saveAggregatedKeywords(keywords: List<DailyKeyword>): Int {
        if (keywords.isEmpty()) return 0

        try {
            val operations = keywords.map { keyword ->
                BulkOperation.Builder()
                    .index(
                        IndexOperation.Builder<DailyKeyword>()
                            .index(keywordIndexName)
                            .id(keyword.toDocumentId())
                            .document(keyword)
                            .build()
                    )
                    .build()
            }

            val bulkRequest = BulkRequest.Builder()
                .operations(operations)
                .build()

            val response = elasticsearchClient.bulk(bulkRequest)

            val successCount = response.items().count { it.error() == null }
            
            if (response.errors()) {
                logger.warn { "Some keywords failed to save: ${response.items().count { it.error() != null }} errors" }
            }

            logger.info { "Saved $successCount/${keywords.size} aggregated keywords" }
            return successCount

        } catch (e: Exception) {
            logger.error(e) { "Failed to save aggregated keywords" }
            return 0
        }
    }

    /**
     * 이전 날짜의 키워드 순위 조회
     */
    private fun getPreviousDayRanks(date: LocalDate): Map<String, Int> {
        return try {
            val termQuery = Query.Builder()
                .term { t -> t.field("date").value(date.toString()) }
                .build()

            val searchRequest = SearchRequest.Builder()
                .index(keywordIndexName)
                .size(100)
                .query(termQuery)
                .sort { s -> s.field { f -> f.field("rank").order(SortOrder.Asc) } }
                .build()

            val response = elasticsearchClient.search(searchRequest, DailyKeyword::class.java)

            response.hits().hits()
                .mapNotNull { it.source() }
                .associate { it.keyword to it.rank }

        } catch (e: Exception) {
            logger.debug { "No previous day data for $date" }
            emptyMap()
        }
    }

    /**
     * 소스별 분포 파싱
     */
    private fun parseSourceBreakdown(bucket: StringTermsBucket): SourceBreakdown {
        val bySource = bucket.aggregations()["by_source"]?.sterms()?.buckets()?.array() 
            ?: return SourceBreakdown()

        var rss = 0
        var wiki = 0
        var twitter = 0

        bySource.forEach { sourceBucket ->
            when (sourceBucket.key().stringValue().uppercase()) {
                "RSS" -> rss = sourceBucket.docCount().toInt()
                "WIKI" -> wiki = sourceBucket.docCount().toInt()
                "TWITTER" -> twitter = sourceBucket.docCount().toInt()
            }
        }

        return SourceBreakdown(rss, wiki, twitter)
    }

    /**
     * 시간별 분포 파싱
     */
    private fun parseHourlyTrend(bucket: StringTermsBucket): List<HourlyCount> {
        val byHour = bucket.aggregations()["by_hour"]?.dateHistogram()?.buckets()?.array() 
            ?: return emptyList()

        return byHour.mapNotNull { hourBucket ->
            try {
                val timestamp = hourBucket.key()
                val dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.systemDefault()
                )
                HourlyCount(hour = dateTime.hour, count = hourBucket.docCount().toInt())
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 점수 계산
     */
    private fun calculateScore(frequency: Long, rank: Int): Float {
        val freqScore = kotlin.math.ln(frequency.toDouble() + 1)
        val rankWeight = 1.0 / kotlin.math.sqrt(rank.toDouble())
        return (freqScore * rankWeight * 100).toFloat()
    }
}
