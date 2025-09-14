package com.javacafe.rtwcollector.filecrawler.model

import java.time.Instant

data class ChunkMetadata(
    val fileId: String,
    val fileName: String,
    val chunkIndex: Int,
    val pageCount: Int,
    val pageIds: List<String>,
    val totalSize: Long,
    val checksum: String,
    val createdAt: Instant
)
