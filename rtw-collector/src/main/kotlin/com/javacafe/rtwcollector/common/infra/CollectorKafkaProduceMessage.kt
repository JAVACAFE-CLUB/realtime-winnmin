package com.javacafe.rtwcollector.common.infra

data class CollectorKafkaProduceMessage (
    val fileId: String,             // 파일 고유 명 (prefix_UUID_yyyyMMddHHmmss)
    val filePath: String,      // 로컬/스토리지 내 JSON 경로
    val fileSize: Long,             // 파일 크기 (바이트)
    val fileChecksum: String,           // 무결성 검증용 (SHA-256 등)
    val source: String,             // 수집 대상 사이트/채널
    val timestamp: String,     // yyyyMMddHHmmss 형식
    val retryCount: Int = 0,        // 재시도 횟수
    val errorMessage: String? = null// 실패 시 에러 메시지
)
