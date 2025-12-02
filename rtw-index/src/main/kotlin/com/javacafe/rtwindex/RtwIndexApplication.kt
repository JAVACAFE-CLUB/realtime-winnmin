package com.javacafe.rtwindex

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling

private val logger = KotlinLogging.logger {}

@SpringBootApplication(
    scanBasePackages = [
        "com.javacafe.rtwindex",
        "com.javacafe.rtwcore"
    ]
)
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
class RtwIndexApplication

fun main(args: Array<String>) {
    logger.info { "🚀 Starting RTW Index Application..." }
    runApplication<RtwIndexApplication>(*args)
    logger.info { "✅ RTW Index Application started successfully" }
}
