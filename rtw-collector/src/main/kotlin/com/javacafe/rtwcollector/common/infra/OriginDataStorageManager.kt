package com.javacafe.rtwcollector.common.infra

/**
 * 원본 데이터 저장을 위한 추상화 인터페이스
 * - 파일 기반 저장 (OriginDataStorageFileManager)
 * - MongoDB 기반 저장 (OriginDataStorageMongoManager)
 * 두 가지 구현체를 유연하게 전환할 수 있도록 설계
 */
interface OriginDataStorageManager {

    /**
     * 파싱된 원본 데이터를 저장 (기존 - 청크 단위)
     *
     * @deprecated 단건 저장 방식(saveSingleOriginData)을 사용하세요
     */
    @Deprecated("Use saveSingleOriginData for individual items")
    suspend fun saveParsedOriginData(
        originData: Any,
        prefix: String
    ): Result<StorageWriteInfo>

    /**
     * 단일 원본 데이터를 저장 (신규 - 단건 저장)
     *
     * @param originData 저장할 단일 데이터 항목
     * @param prefix 데이터 소스 접두사 (twitter, rss, wiki)
     * @param metadata 추가 메타데이터 (선택 사항)
     * @return Result<StorageWriteInfo> 저장 결과 정보
     */
    suspend fun saveSingleOriginData(
        originData: Any,
        prefix: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<StorageWriteInfo>

    /**
     * 여러 원본 데이터를 배치로 저장 (신규 - 배치 단건 저장)
     *
     * @param originDataList 저장할 데이터 리스트
     * @param prefix 데이터 소스 접두사
     * @param metadata 공통 메타데이터
     * @return Result<List<StorageWriteInfo>> 각 항목의 저장 결과
     */
    suspend fun saveBatchOriginData(
        originDataList: List<Any>,
        prefix: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<List<StorageWriteInfo>>

    /**
     * 저장된 데이터 조회 (선택적 구현)
     */
    suspend fun retrieveOriginData(id: String): Result<Any?> =
        Result.failure(NotImplementedError("조회 기능은 구현되지 않았습니다"))
}
