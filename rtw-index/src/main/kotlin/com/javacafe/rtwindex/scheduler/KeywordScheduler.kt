package com.javacafe.rtwindex.scheduler

import com.javacafe.rtwindex.service.KeywordAggregationService
import com.javacafe.rtwindex.service.KeywordCacheService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * 키워드 집계 스케줄러
 * 
 * 주기적으로 키워드 통계를 집계하고 캐시를 갱신
 * 
 * 스케줄:
 * - 매 10분: 실시간 Top 키워드 갱신 (ES → Redis)
 * - 매 시간: 시간별 키워드 집계 (ES 저장)
 * - 매일 자정: 일별 키워드 집계 (ES 저장)
 * - 매일 새벽 3시: 오래된 캐시 정리
 */
@Component
class KeywordScheduler(
    private val keywordAggregationService: KeywordAggregationService,
    private val keywordCacheService: KeywordCacheService
) {

    /**
     * 실시간 키워드 갱신 (1분마다)
     * 
     * 오늘의 키워드를 집계하고 ES + Redis에 저장
     */
    @Scheduled(fixedRate = 600_00)  // 1분
    fun refreshRealtimeKeywords() {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            logger.info { "🔄 [Scheduler] Starting realtime keyword refresh..." }
            
            val today = LocalDate.now()
            val response = keywordCacheService.refreshDailyKeywords(today)
            
            val duration = System.currentTimeMillis() - startTime
            
            if (response.keywords.isEmpty()) {
                logger.warn { "⚠️ [Scheduler] No keywords found for $today" }
            } else {
                logger.info { "✅ [Scheduler] Refresh complete: ${response.keywords.size} keywords, duration=${duration}ms" }
                
                // Top 3 키워드 로깅
                logger.info { "📊 Top 3 Keywords:" }
                response.keywords.take(3).forEachIndexed { index, kw ->
                    logger.info { "   ${index + 1}. ${kw.keyword} (freq=${kw.frequency}, score=${"%.2f".format(kw.score)})" }
                }
            }
            
            // 캐시 상태 확인
            val cacheStatus = keywordCacheService.getCacheStatus()
            logger.info { "📦 Cache status: ${cacheStatus["totalCachedKeys"]} keys, connected=${cacheStatus["redisConnected"]}" }
            logger.info { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ [Scheduler] Failed to refresh realtime keywords" }
        }
    }

    /**
     * 시간별 키워드 집계 (매 시간 정각)
     * 
     * ES에만 저장 (rtw-keywords 인덱스)
     */
    @Scheduled(cron = "0 0 * * * *")  // 매 시간 정각
    fun aggregateHourlyKeywords() {
        val startTime = System.currentTimeMillis()
        val now = LocalDateTime.now()
        
        try {
            logger.info { "⏰ [Scheduler] Starting hourly keyword aggregation for ${now.hour}:00..." }
            
            val keywords = keywordAggregationService.aggregateHourlyKeywords(now)
            
            if (keywords.isEmpty()) {
                logger.warn { "⚠️ [Scheduler] No hourly keywords found for ${now.hour}:00" }
                return
            }
            
            // ES에 저장
            val savedCount = keywordAggregationService.saveAggregatedKeywords(keywords)
            
            val duration = System.currentTimeMillis() - startTime
            logger.info { 
                "✅ [Scheduler] Hourly aggregation complete: " +
                "${keywords.size} keywords aggregated, $savedCount saved to ES, duration=${duration}ms"
            }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ [Scheduler] Failed to aggregate hourly keywords" }
        }
    }

    /**
     * 일별 키워드 집계 (매일 자정 5분)
     * 
     * 전날의 최종 통계를 집계하고 ES에 저장
     */
    @Scheduled(cron = "0 5 0 * * *")  // 매일 00:05
    fun aggregateDailyKeywords() {
        val startTime = System.currentTimeMillis()
        val yesterday = LocalDate.now().minusDays(1)
        
        try {
            logger.info { "📅 [Scheduler] Starting daily keyword aggregation for $yesterday..." }
            
            // 전날 최종 집계 (Top 100)
            val keywords = keywordAggregationService.aggregateDailyKeywords(yesterday, 100)
            
            if (keywords.isEmpty()) {
                logger.warn { "⚠️ [Scheduler] No daily keywords found for $yesterday" }
                return
            }
            
            // ES에 저장
            val savedCount = keywordAggregationService.saveAggregatedKeywords(keywords)
            
            val duration = System.currentTimeMillis() - startTime
            logger.info { 
                "✅ [Scheduler] Daily aggregation complete for $yesterday: " +
                "${keywords.size} keywords, $savedCount saved to ES, duration=${duration}ms"
            }
            
            // Top 10 로깅
            logger.info { "📊 Yesterday's Top 10 Keywords:" }
            keywords.take(10).forEachIndexed { index, kw ->
                val trend = when {
                    kw.isNew -> "🆕 NEW"
                    (kw.rankChange ?: 0) > 0 -> "⬆️ +${kw.rankChange}"
                    (kw.rankChange ?: 0) < 0 -> "⬇️ ${kw.rankChange}"
                    else -> "➡️"
                }
                logger.info { "   ${index + 1}. ${kw.keyword} $trend (freq=${kw.frequency})" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ [Scheduler] Failed to aggregate daily keywords for $yesterday" }
        }
    }

    /**
     * 캐시 정리 (매일 새벽 3시)
     * 
     * 7일 이전의 캐시 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")  // 매일 03:00
    fun cleanupOldCaches() {
        try {
            logger.info { "🧹 [Scheduler] Starting cache cleanup..." }
            
            var deletedCount = 0
            
            // 8일~30일 전 캐시 삭제
            (8..30).forEach { daysAgo ->
                val date = LocalDate.now().minusDays(daysAgo.toLong())
                keywordCacheService.evictCache(date)
                deletedCount++
            }
            
            logger.info { "✅ [Scheduler] Cache cleanup complete: checked $deletedCount old dates" }
            
            // 현재 캐시 상태
            val status = keywordCacheService.getCacheStatus()
            logger.info { "📦 Current cache: ${status["totalCachedKeys"]} keys remaining" }
            
        } catch (e: Exception) {
            logger.error(e) { "❌ [Scheduler] Failed to cleanup caches" }
        }
    }

    /**
     * 헬스 체크용 상태 조회
     */
    fun getSchedulerStatus(): Map<String, Any> {
        return mapOf(
            "schedulerActive" to true,
            "lastCheck" to LocalDateTime.now().toString(),
            "cacheStatus" to keywordCacheService.getCacheStatus()
        )
    }
}
