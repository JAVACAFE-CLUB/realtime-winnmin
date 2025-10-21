package com.javacafe.rtwcollector.common.infra.mongodb

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * MongoDB에 저장되는 원본 크롤링 데이터 Document
 *
 * 컬렉션명: origin_data
 *
 * 인덱스 전략:
 * 1. createdAt (DESC) - 최신 데이터 조회
 * 2. prefix + createdAt (복합) - 소스별 시계열 조회
 * 3. source - 수집 타입별 필터링
 * 4. createdAt (TTL) - 30일 후 자동 삭제
 */
@Document(collection = "origin_data")
@CompoundIndexes(
    CompoundIndex(
        name = "prefix_createdAt_idx",
        def = "{'prefix': 1, 'createdAt': -1}"
    ),
    CompoundIndex(
        name = "source_createdAt_idx",
        def = "{'source': 1, 'createdAt': -1}"
    )
)
data class OriginDataDocument(
    /**
     * MongoDB의 고유 식별자
     * 형식: "{prefix}_{yyyyMMddHHmmss}_{uuid}"
     * 예: "twitter_20250915123045_a1b2c3d4-e5f6-7890-abcd-ef1234567890"
     */
    @Id
    val id: String,

    /**
     * 데이터 소스 접두사
     * - "twitter": 트위터 크롤링
     * - "rss": RSS 피드 크롤링
     * - "wiki": 위키피디아 파일 크롤링
     */
    @Indexed
    val prefix: String,

    /**
     * 크롤링 소스 타입 (CollectorConstant의 값)
     * - "twitter_api": Twitter API 수집
     * - "rss_feed": RSS 피드 수집
     * - "wiki_file": 위키 파일 수집
     */
    @Indexed
    val source: String,

    /**
     * 실제 크롤링된 원본 데이터 (JSON 형태로 저장)
     *
     * 타입별 예시:
     * - Twitter: TwitterApiResponse 객체
     * - RSS: List<RssItem> 객체
     * - Wiki: WikiFileData 객체
     *
     * MongoDB는 자동으로 BSON으로 변환하여 저장
     */
    val data: Any,

    /**
     * 원본 데이터의 바이트 크기
     * JSON 직렬화된 문자열의 바이트 길이
     */
    val dataSize: Long,

    /**
     * 데이터 무결성 검증용 SHA-256 체크섬
     * data 필드를 JSON으로 직렬화한 후 해시 계산
     */
    val checksum: String,

    /**
     * 생성 시간 (자동 생성)
     * - 조회 및 정렬에 사용
     * - TTL 인덱스의 기준 필드
     */
    @Indexed(expireAfterSeconds = 2592000)  // 30일 = 30 * 24 * 60 * 60
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 추가 메타데이터 (확장 가능한 Map)
     *
     * 예시:
     * - "query": "검색 쿼리"
     * - "maxResults": "요청한 최대 결과 수"
     * - "actualCount": "실제 수집된 개수"
     * - "apiVersion": "API 버전"
     */
    val metadata: Map<String, Any> = emptyMap(),

    /**
     * 수정 시간 (업데이트 추적용, 선택 사항)
     */
    val updatedAt: LocalDateTime? = null
)
