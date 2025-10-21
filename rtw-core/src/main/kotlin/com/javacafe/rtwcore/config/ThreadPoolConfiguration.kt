package com.javacafe.rtwcore.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableConfigurationProperties
class ThreadPoolConfiguration {

    @Bean("ioThreadPoolTaskExecutor")
    @Primary
    fun ioThreadPoolTaskExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 8
            maxPoolSize = 16
            queueCapacity = 100
            setThreadNamePrefix("io-thread-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }
    }
}

