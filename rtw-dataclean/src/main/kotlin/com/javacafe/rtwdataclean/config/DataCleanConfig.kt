package com.javacafe.rtwdataclean.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DataCleanConfig {

    @Value("\${app.processing.parallelism:30}")
    private var parallelism: Int = 30

    /**
     * 데이터 정재 처리용 Dispatcher
     * 병렬 처리 수를 제한하여 리소스 관리
     */
    @Bean("dataCleanDispatcher")
    fun dataCleanDispatcher(): CoroutineDispatcher {
        return Dispatchers.IO.limitedParallelism(parallelism)
    }
}
