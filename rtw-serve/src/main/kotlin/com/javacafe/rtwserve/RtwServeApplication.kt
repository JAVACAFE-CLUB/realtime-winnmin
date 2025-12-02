package com.javacafe.rtwserve

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class RtwServeApplication

fun main(args: Array<String>) {
    runApplication<RtwServeApplication>(*args)
}
