package com.javacafe.rtwindex.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Redis 설정
 * 
 * 키워드 캐싱 및 일별 통계 저장용
 */
@Configuration
@EnableCaching
class RedisConfig {

    /**
     * Redis ObjectMapper
     */
    @Bean("redisObjectMapper")
    fun redisObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    /**
     * JSON 직렬화를 사용하는 RedisTemplate
     */
    @Bean("jsonRedisTemplate")
    @Primary
    fun jsonRedisTemplate(
        connectionFactory: RedisConnectionFactory
    ): RedisTemplate<String, Any> {
        val objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = jsonSerializer
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = jsonSerializer
        }.also {
            logger.info { "JSON RedisTemplate configured" }
        }
    }

    /**
     * 캐시 매니저 설정
     */
    @Bean
    fun cacheManager(
        connectionFactory: RedisConnectionFactory
    ): CacheManager {
        val objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        // 기본 캐시 설정 (24시간 TTL)
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(24))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
            )
            .disableCachingNullValues()

        // 캐시별 설정
        val cacheConfigurations = mapOf(
            // 오늘의 키워드 (1시간 TTL)
            "dailyKeywords" to defaultConfig.entryTtl(Duration.ofHours(1)),
            // 시간별 키워드 (30분 TTL)
            "hourlyKeywords" to defaultConfig.entryTtl(Duration.ofMinutes(30)),
            // 키워드 트렌드 (6시간 TTL)
            "keywordTrends" to defaultConfig.entryTtl(Duration.ofHours(6))
        )

        logger.info { "Redis CacheManager configured with caches: ${cacheConfigurations.keys}" }

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
