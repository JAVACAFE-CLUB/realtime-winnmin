package com.javacafe.rtwindex.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

private val logger = KotlinLogging.logger {}

/**
 * MongoDB 설정
 * 
 * rtw-dataclean이 저장한 full_text_data 컬렉션을 조회
 */
@Configuration
@EnableMongoRepositories(basePackages = ["com.javacafe.rtwindex.repository"])
class MongoConfig {
    init {
        logger.info { "MongoDB Repository enabled for rtw-index" }
    }
}
