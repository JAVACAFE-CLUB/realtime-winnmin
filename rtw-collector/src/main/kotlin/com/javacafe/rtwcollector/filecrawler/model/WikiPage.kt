package com.javacafe.rtwcollector.filecrawler.model

data class WikiPage(
    val title: String,
    val id: String,
    val ns: String = "0",
    val revision: Revision
)
