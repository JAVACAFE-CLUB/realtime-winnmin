package com.javacafe.rtwcollector.filecrawler.model

import java.time.Instant
import java.util.UUID

data class ChunkFile(
    val fileId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val chunkIndex: Int,
    val pageCount: Int,
    val totalSize: Long,
    val createdAt: Instant = Instant.now()
)
