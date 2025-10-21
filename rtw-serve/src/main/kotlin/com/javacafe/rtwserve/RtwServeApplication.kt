package com.javacafe.rtwserve

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RtwServingApplication

fun main(args: Array<String>) {
    runApplication<RtwServingApplication>(*args)
}
