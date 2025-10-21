package com.javacafe.rtwcollector.filecrawler.model

data class Revision(
    val id: String,
    val parentId: String?,
    val timestamp: String,
    val contributor: Contributor,
    val text: String,
    val sha1: String
) {
    companion object {
        fun empty() = Revision("", null, "", Contributor.empty(), "", "")
    }
}
