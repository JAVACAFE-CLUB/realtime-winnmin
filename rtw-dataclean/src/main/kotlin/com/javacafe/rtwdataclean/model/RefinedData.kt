package com.javacafe.rtwdataclean.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 정재된 데이터 (Kafka로 전송)
 * 
 * 경량화된 메시지: ID와 메타데이터만 포함
 * 풀텍스트와 원본 데이터는 MongoDB의 full_text_data 컬렉션에 저장됨
 */
data class RefinedData(
    /**
     * 정제된 데이터의 고유 ID
     * MongoDB full_text_data 컬렉션의 _id로 사용됨
     */
    @JsonProperty("refined_id")
    val refinedId: String,
    
    /**
     * 메타데이터 (처리 정보, 타임스탬프 등)
     */
    @JsonProperty("metadata")
    val metadata: Metadata
)
