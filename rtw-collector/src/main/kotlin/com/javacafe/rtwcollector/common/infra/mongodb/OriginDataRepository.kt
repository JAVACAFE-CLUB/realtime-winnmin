package com.javacafe.rtwcollector.common.infra.mongodb

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * OriginDataDocument를 위한 MongoDB Repository
 *
 * Spring Data MongoDB가 제공하는 기본 CRUD 외에
 * 커스텀 쿼리 메서드들을 정의
 */
@Repository
interface OriginDataRepository : MongoRepository<OriginDataDocument, String> {

    /**
     * prefix로 데이터 조회 (페이징)
     * 예: "twitter" prefix로 트위터 데이터만 조회
     */
    fun findByPrefix(prefix: String, pageable: Pageable): Page<OriginDataDocument>

    /**
     * source로 데이터 조회 (페이징)
     * 예: "twitter_api" source로 필터링
     */
    fun findBySource(source: String, pageable: Pageable): Page<OriginDataDocument>

    /**
     * 특정 기간 내 생성된 데이터 조회
     */
    fun findByCreatedAtBetween(
        start: LocalDateTime,
        end: LocalDateTime,
        pageable: Pageable
    ): Page<OriginDataDocument>

    /**
     * prefix와 기간으로 조회
     */
    fun findByPrefixAndCreatedAtBetween(
        prefix: String,
        start: LocalDateTime,
        end: LocalDateTime,
        pageable: Pageable
    ): Page<OriginDataDocument>

    /**
     * 특정 prefix의 총 데이터 크기 계산
     */
    @Query("{ 'prefix': ?0 }")
    fun sumDataSizeByPrefix(prefix: String): Long?

    /**
     * prefix별 데이터 개수 카운트
     */
    fun countByPrefix(prefix: String): Long

    /**
     * 오래된 데이터 삭제 (TTL 외 수동 삭제가 필요한 경우)
     */
    fun deleteByCreatedAtBefore(dateTime: LocalDateTime): Long
}
