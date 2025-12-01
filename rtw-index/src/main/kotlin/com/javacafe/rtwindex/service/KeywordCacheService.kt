package com.javacafe.rtwindex.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.javacafe.rtwcore.model.es.DailyKeyword
import com.javacafe.rtwcore.model.es.TopKeywordsResponse
import com.javacafe.rtwindex.config.IndexProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * 키워드 캐시 서비스
 * 
 * Redis를 사용하여 오늘의 키워드를 캐싱
 * 
 * 캐시 구조:
 * - rtw:keyword:daily:{date} → Top 10 키워드 (JSON)
 * - rtw:keyword:hourly:{date}:{hour} → 시간별 Top 10
 */
@Service
class KeywordCacheService(
    @Qualifier("jsonRedisTemplate")
    private val redisTemplate: RedisTemplate<String, Any>,
    private val keywordAggregationService: KeywordAggregationService,
    private val indexProperties: IndexProperties,
    private val objectMapper: ObjectMapper
) {
    private val cachePrefix: String
        get() = indexProperties.keyword.cachePrefix

    private val cacheTtl: Long
        get() = indexProperties.keyword.cacheTtl

    private val topCount: Int
        get() = indexProperties.keyword.topCount

    /**
     * 오늘의 Top 키워드 조회 (캐시 우선)
     */
    fun getDailyTopKeywords(date: LocalDate = LocalDate.now()): TopKeywordsResponse {
        // 1. 캐시에서 먼저 조회
        val cached = getFromCache(date)
        if (cached != null) {
            logger.debug { "Cache hit for daily keywords: $date" }
            return cached
        }
        
        // 2. 캐시 미스 → ES에서 집계
        logger.info { "Cache miss for daily keywords: $date, aggregating from ES..." }
        val keywords = keywordAggregationService.aggregateDailyKeywords(date, topCount)
        
        val response = TopKeywordsResponse(
            date = date,
            keywords = keywords,
            generatedAt = LocalDateTime.now()
        )
        
        // 3. 캐시에 저장
        putToCache(date, response)
        
        return response
    }

    /**
     * 시간별 Top 키워드 조회 (캐시 우선)
     */
    fun getHourlyTopKeywords(dateTime: LocalDateTime = LocalDateTime.now()): TopKeywordsResponse {
        val date = dateTime.toLocalDate()
        val hour = dateTime.hour
        val cacheKey = "${cachePrefix}hourly:${date}:${hour}"
        
        // 1. 캐시에서 먼저 조회
        val cached = getFromCacheByKey(cacheKey)
        if (cached != null) {
            logger.debug { "Cache hit for hourly keywords: $dateTime" }
            return cached
        }
        
        // 2. 캐시 미스 → ES에서 집계
        logger.info { "Cache miss for hourly keywords: $dateTime, aggregating from ES..." }
        val keywords = keywordAggregationService.aggregateHourlyKeywords(dateTime, topCount)
        
        val response = TopKeywordsResponse(
            date = date,
            keywords = keywords,
            generatedAt = LocalDateTime.now()
        )
        
        // 3. 캐시에 저장 (1시간 TTL)
        putToCacheByKey(cacheKey, response, 3600)
        
        return response
    }

    /**
     * 캐시 수동 갱신 (스케줄러에서 호출)
     * 
     * ES에서 집계 → ES 저장 → Redis 저장
     */
    fun refreshDailyKeywords(date: LocalDate = LocalDate.now()): TopKeywordsResponse {
        logger.info { "🔄 Refreshing daily keywords for $date..." }
        
        // 1. ES에서 집계
        val keywords = keywordAggregationService.aggregateDailyKeywords(date, topCount)
        
        val response = TopKeywordsResponse(
            date = date,
            keywords = keywords,
            generatedAt = LocalDateTime.now()
        )
        
        // 2. ES에 저장 (rtw-keywords 인덱스)
        val esSavedCount = keywordAggregationService.saveAggregatedKeywords(keywords)
        logger.info { "📊 Saved $esSavedCount keywords to ES (rtw-keywords)" }
        
        // 3. Redis에 저장
        val redisSaved = putToCache(date, response)
        if (redisSaved) {
            logger.info { "✅ Cached ${keywords.size} keywords to Redis (key: ${cachePrefix}${date})" }
        } else {
            logger.error { "❌ Failed to cache keywords to Redis" }
        }
        
        return response
    }

    /**
     * Redis에 직접 저장
     * 
     * @return 저장 성공 여부
     */
    fun putToCache(date: LocalDate, response: TopKeywordsResponse): Boolean {
        val key = "${cachePrefix}${date}"
        return putToCacheByKey(key, response, cacheTtl)
    }

    /**
     * 키를 지정하여 Redis에 저장
     */
    private fun putToCacheByKey(key: String, response: TopKeywordsResponse, ttlSeconds: Long): Boolean {
        return try {
            redisTemplate.opsForValue().set(key, response, Duration.ofSeconds(ttlSeconds))
            logger.info { "Redis SET: key=$key, ttl=${ttlSeconds}s, keywords=${response.keywords.size}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Redis SET failed: key=$key" }
            false
        }
    }

    /**
     * Redis에서 직접 조회
     */
    fun getFromCache(date: LocalDate): TopKeywordsResponse? {
        val key = "${cachePrefix}${date}"
        return getFromCacheByKey(key)
    }

    /**
     * 키를 지정하여 Redis에서 조회
     */
    private fun getFromCacheByKey(key: String): TopKeywordsResponse? {
        return try {
            val value = redisTemplate.opsForValue().get(key)
            if (value != null) {
                logger.debug { "Redis GET: key=$key, found=true" }
                // ObjectMapper로 변환 (타입 안전성)
                objectMapper.convertValue(value, TopKeywordsResponse::class.java)
            } else {
                logger.debug { "Redis GET: key=$key, found=false" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Redis GET failed: key=$key" }
            null
        }
    }

    /**
     * 특정 날짜 캐시 삭제
     */
    fun evictCache(date: LocalDate) {
        val key = "${cachePrefix}${date}"
        try {
            val deleted = redisTemplate.delete(key)
            logger.info { "Redis DEL: key=$key, deleted=$deleted" }
        } catch (e: Exception) {
            logger.error(e) { "Redis DEL failed: key=$key" }
        }
    }

    /**
     * 모든 키워드 캐시 삭제
     */
    fun evictAllCaches() {
        try {
            val pattern = "${cachePrefix}*"
            val keys = redisTemplate.keys(pattern)
            if (keys.isNotEmpty()) {
                val deletedCount = redisTemplate.delete(keys)
                logger.info { "Redis DEL pattern=$pattern, deleted=$deletedCount keys" }
            } else {
                logger.info { "Redis DEL pattern=$pattern, no keys found" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Redis DEL pattern failed" }
        }
    }

    /**
     * 캐시 상태 조회
     */
    fun getCacheStatus(): Map<String, Any> {
        return try {
            val pattern = "${cachePrefix}*"
            val keys = redisTemplate.keys(pattern)
            
            val status = mapOf(
                "totalCachedKeys" to keys.size,
                "cachePrefix" to cachePrefix,
                "ttlSeconds" to cacheTtl,
                "cachedKeys" to keys.sorted(),
                "redisConnected" to testRedisConnection()
            )
            
            logger.info { "Cache status: ${keys.size} keys, prefix=$cachePrefix" }
            status
        } catch (e: Exception) {
            logger.error(e) { "Failed to get cache status" }
            mapOf(
                "error" to e.message.orEmpty(),
                "redisConnected" to false
            )
        }
    }

    /**
     * Redis 연결 테스트
     */
    private fun testRedisConnection(): Boolean {
        return try {
            redisTemplate.connectionFactory?.connection?.ping() != null
        } catch (e: Exception) {
            logger.warn { "Redis connection test failed: ${e.message}" }
            false
        }
    }

    /**
     * 최근 N일 키워드 조회 (캐시 활용)
     */
    fun getRecentKeywords(days: Int = 7): List<TopKeywordsResponse> {
        val today = LocalDate.now()
        return (0 until days).map { offset ->
            val date = today.minusDays(offset.toLong())
            getFromCache(date) ?: getDailyTopKeywords(date)
        }
    }
}
