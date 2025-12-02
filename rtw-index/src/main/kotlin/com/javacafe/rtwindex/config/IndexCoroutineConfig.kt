package com.javacafe.rtwindex.config

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val logger = KotlinLogging.logger {}

/**
 * rtw-index 전용 Coroutine Dispatcher 설정
 * 
 * 고성능 비동기 처리를 위한 Dispatcher 구성
 */
@Configuration
class IndexCoroutineConfig {

    @Value("\${app.processing.parallelism:30}")
    private var parallelism: Int = 30

    /**
     * MongoDB 조회용 Dispatcher
     */
    @Bean(name = ["mongoDispatcher"])
    fun mongoDispatcher(): CoroutineDispatcher {
        logger.info { "MongoDB Dispatcher: parallelism=$parallelism" }
        return Dispatchers.IO.limitedParallelism(parallelism)
    }

    /**
     * Elasticsearch 색인용 Dispatcher
     */
    @Bean(name = ["elasticsearchDispatcher"])
    fun elasticsearchDispatcher(): CoroutineDispatcher {
        logger.info { "Elasticsearch Dispatcher: parallelism=$parallelism" }
        return Dispatchers.IO.limitedParallelism(parallelism)
    }

    /**
     * 형태소 분석용 Dispatcher (CPU 바운드)
     */
    @Bean(name = ["analysisDispatcher"])
    fun analysisDispatcher(): CoroutineDispatcher {
        val cpuParallelism = Runtime.getRuntime().availableProcessors()
        logger.info { "Analysis Dispatcher: parallelism=$cpuParallelism (CPU cores)" }
        return Dispatchers.Default.limitedParallelism(cpuParallelism)
    }
}
