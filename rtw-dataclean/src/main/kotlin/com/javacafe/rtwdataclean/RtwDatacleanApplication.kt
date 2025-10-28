package com.javacafe.rtwdataclean

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "com.javacafe.rtwdataclean",
        "com.javacafe.rtwcore"  // rtw-core 스캔
    ]
)
class RtwDatacleanApplication

fun main(args: Array<String>) {
    runApplication<RtwDatacleanApplication>(*args)
}
