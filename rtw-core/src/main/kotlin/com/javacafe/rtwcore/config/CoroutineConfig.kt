package com.javacafe.rtwcore.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import java.util.concurrent.Executors

@Configuration
class CoroutineConfig {
    @Bean(name = ["ioDispatcher"])
    @Primary
    fun ioDispatcher(): CoroutineDispatcher = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        ).asCoroutineDispatcher()

    @Bean(name = ["writeDispatcher"])
    fun writeDispatcher(): CoroutineDispatcher = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        ).asCoroutineDispatcher()
}
