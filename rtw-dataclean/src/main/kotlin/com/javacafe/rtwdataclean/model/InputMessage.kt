package com.javacafe.rtwdataclean.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Kafka로부터 수신하는 입력 메시지
 * Collector에서 전송한 CollectorKafkaProduceMessage 포맷
 */
data class InputMessage(
    @JsonProperty("fileId")
    val fileId: String,             // MongoDB의 document ID
    
    @JsonProperty("filePath")
    val filePath: String,           // 로컬/스토리지 내 JSON 경로
    
    @JsonProperty("fileSize")
    val fileSize: Long,             // 파일 크기 (바이트)
    
    @JsonProperty("fileChecksum")
    val fileChecksum: String,       // 무결성 검증용
    
    @JsonProperty("source")
    val source: String,             // 수집 대상 사이트/채널
    
    @JsonProperty("timestamp")
    val timestamp: String,          // yyyyMMddHHmmss 형식
    
    @JsonProperty("retryCount")
    val retryCount: Int = 0,
    
    @JsonProperty("errorMessage")
    val errorMessage: String? = null
)
