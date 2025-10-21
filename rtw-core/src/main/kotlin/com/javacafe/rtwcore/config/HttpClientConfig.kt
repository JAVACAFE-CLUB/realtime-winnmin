package com.javacafe.rtwcore.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class HttpClientConfig(
    @Qualifier("ioThreadPoolTaskExecutor") private val ioExecutor: ThreadPoolTaskExecutor
) {

    @Bean
    fun httpClient(): HttpClient {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(ioExecutor.threadPoolExecutor) // 스프링이 관리하는 스레드풀 사용
            .build()
    }
}
