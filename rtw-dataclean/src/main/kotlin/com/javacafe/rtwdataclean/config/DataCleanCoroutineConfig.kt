package com.javacafe.rtwdataclean.config

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DataCleanCoroutineConfig {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Value("\${app.processing.parallelism:30}")
    private var parallelism: Int = 30

    @Bean(name = ["processingDispatcher"])
    fun processingDispatcher(): CoroutineDispatcher {
        logger.info { "Processing Dispatcher Configuration: parallelism=$parallelism" }
        return Dispatchers.IO.limitedParallelism(parallelism)
    }
}
