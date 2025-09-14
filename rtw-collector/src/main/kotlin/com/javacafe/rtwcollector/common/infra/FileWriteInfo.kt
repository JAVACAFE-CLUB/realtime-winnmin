package com.javacafe.rtwcollector.common.infra

data class FileWriteInfo(
    val fileId: String,
    val path: String,
    val fileSize: Long, // 파일 크기 (바이트)
    val checksum: String // 무결성 검증용 (SHA-256 등)
)
