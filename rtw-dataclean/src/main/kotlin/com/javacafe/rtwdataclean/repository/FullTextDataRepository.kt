package com.javacafe.rtwdataclean.repository

import com.javacafe.rtwdataclean.model.FullTextDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 풀텍스트 데이터를 MongoDB에 저장/조회하는 Repository
 * 
 * Spring Data MongoDB의 MongoRepository를 상속하여
 * 기본 CRUD 및 커스텀 쿼리 메서드 제공
 * 
 * 컬렉션: full_text_data
 */
@Repository
interface FullTextDataRepository : MongoRepository<FullTextDocument, String> {

    /**
     * refinedId로 풀텍스트 데이터 조회 (메인 조회 메서드)
     * 
     * rtw-index 모듈에서 Elasticsearch 인덱싱 시 사용
     * 
     * @param refinedId Kafka 메시지의 refinedId
     * @return 풀텍스트 데이터 또는 null
     */
    fun findByRefinedId(refinedId: String): FullTextDocument?

    /**
     * originId로 풀텍스트 데이터 조회
     * 
     * 원본 데이터 참조가 필요한 경우 사용
     * 
     * @param originId 원본 데이터 ID
     * @return 풀텍스트 데이터 또는 null
     */
    fun findByOriginId(originId: String): FullTextDocument?

    /**
     * sourceType으로 데이터 조회 (페이징)
     * 
     * @param sourceType RSS, WIKI, TWITTER 등
     * @param pageable 페이징 정보
     * @return 페이징된 풀텍스트 데이터
     */
    fun findBySourceType(sourceType: String, pageable: Pageable): Page<FullTextDocument>

    /**
     * 특정 기간 내 생성된 데이터 조회
     * 
     * @param start 시작 시간
     * @param end 종료 시간
     * @param pageable 페이징 정보
     * @return 페이징된 풀텍스트 데이터
     */
    fun findByCreatedAtBetween(
        start: LocalDateTime,
        end: LocalDateTime,
        pageable: Pageable
    ): Page<FullTextDocument>

    /**
     * sourceType과 기간으로 조회
     * 
     * @param sourceType 소스 타입
     * @param start 시작 시간
     * @param end 종료 시간
     * @param pageable 페이징 정보
     * @return 페이징된 풀텍스트 데이터
     */
    fun findBySourceTypeAndCreatedAtBetween(
        sourceType: String,
        start: LocalDateTime,
        end: LocalDateTime,
        pageable: Pageable
    ): Page<FullTextDocument>

    /**
     * 추출 성공 여부로 조회
     * 
     * 에러 분석이나 재처리가 필요한 경우 사용
     * 
     * @param extractionSuccess 추출 성공 여부
     * @param pageable 페이징 정보
     * @return 페이징된 풀텍스트 데이터
     */
    fun findByExtractionSuccess(
        extractionSuccess: Boolean,
        pageable: Pageable
    ): Page<FullTextDocument>

    /**
     * sourceType별 데이터 개수 카운트
     * 
     * @param sourceType 소스 타입
     * @return 데이터 개수
     */
    fun countBySourceType(sourceType: String): Long

    /**
     * 추출 성공/실패 개수 카운트
     * 
     * @param extractionSuccess 추출 성공 여부
     * @return 데이터 개수
     */
    fun countByExtractionSuccess(extractionSuccess: Boolean): Long

    /**
     * refinedId 존재 여부 확인
     * 
     * 중복 체크에 사용
     * 
     * @param refinedId 정제 데이터 ID
     * @return 존재 여부
     */
    fun existsByRefinedId(refinedId: String): Boolean
}
