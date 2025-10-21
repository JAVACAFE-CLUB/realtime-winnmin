package com.javacafe.rtwcollector

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.javacafe.rtwcollector",
        "com.javacafe.rtwcore"
    ]
)
class RtwCollectorApplication

fun main(args: Array<String>) {
    runApplication<RtwCollectorApplication>(*args)
}
