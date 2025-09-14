package com.javacafe.rtwcollector.filecrawler.dto

data class ChunkResult(
    val chunkIndex: Int,
    val pageCount: Int,
    val fileId: String,
    val fileSize: Long
)
