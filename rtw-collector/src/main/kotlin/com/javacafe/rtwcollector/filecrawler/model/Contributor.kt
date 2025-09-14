package com.javacafe.rtwcollector.filecrawler.model

data class Contributor(
    val username: String?,
    val ip: String?,
    val id: String?
) {
    companion object {
        fun empty() = Contributor(null, null, null)
    }
}
