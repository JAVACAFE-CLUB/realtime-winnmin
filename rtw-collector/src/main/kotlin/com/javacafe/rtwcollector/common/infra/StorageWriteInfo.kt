package com.javacafe.rtwcollector.common.infra

/**
 * 저장소 타입에 관계없이 공통으로 사용되는 저장 결과 정보
 *
 * FileWriteInfo와 호환되도록 설계되었으며,
 * MongoDB 저장 시에도 동일한 구조로 반환됩니다.
 */
data class StorageWriteInfo(
    /**
     * 저장된 데이터의 고유 식별자
     * - 파일 저장: "{prefix}_{timestamp}_{uuid}.json" (확장자 제외한 ID)
     * - MongoDB 저장: ObjectId 또는 Custom ID
     */
    val id: String,

    /**
     * 저장 위치 정보
     * - 파일 저장: 절대 경로 (예: /Users/usmin/RtwOriginData/twitter_20250101_120000_uuid.json)
     * - MongoDB 저장: 컬렉션명 (예: origin_data)
     */
    val storageLocation: String,

    /**
     * 저장된 데이터의 크기 (바이트 단위)
     * - 파일 저장: 파일 크기
     * - MongoDB 저장: JSON 직렬화된 바이트 크기
     */
    val dataSize: Long,

    /**
     * 데이터 무결성 검증을 위한 체크섬 (SHA-256)
     * - 파일 저장: 파일 내용의 해시값
     * - MongoDB 저장: JSON 문자열의 해시값
     */
    val checksum: String,

    /**
     * 추가 메타데이터 (확장 가능)
     * 예: 저장 타입 ("file" or "mongodb"), 압축 여부 등
     */
    val metadata: Map<String, String> = emptyMap()
)
