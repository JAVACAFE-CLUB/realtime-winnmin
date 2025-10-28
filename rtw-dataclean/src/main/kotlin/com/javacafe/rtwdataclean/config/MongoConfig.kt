package com.javacafe.rtwdataclean.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

/**
 * MongoDB 설정
 * 
 * rtw-dataclean 모듈용 MongoDB 클라이언트 및 데이터베이스 설정
 * rtw-collector와 동일한 패턴 사용
 */
@Configuration
class MongoConfig {

    companion object {
        private val logger = KotlinLogging.logger { }
        private const val COLLECTION_NAME = "full_text_data"
        private val TTL_DURATION = Duration.ofDays(30)
    }

    /**
     * LocalDateTime <-> Date 변환을 위한 커스텀 Converter 등록
     */
    @Bean
    fun mongoCustomConversions(): MongoCustomConversions {
        return MongoCustomConversions(
            listOf(
                LocalDateTimeToDateConverter(),
                DateToLocalDateTimeConverter()
            )
        )
    }

    /**
     * MongoTemplate 커스터마이징
     *
     * "_class" 필드 제거 및 커스텀 Converter 적용
     */
    @Bean
    fun mongoTemplate(
        mongoDatabaseFactory: MongoDatabaseFactory,
        mongoMappingContext: MongoMappingContext,
        mongoCustomConversions: MongoCustomConversions
    ): MongoTemplate {
        val converter = MappingMongoConverter(
            DefaultDbRefResolver(mongoDatabaseFactory),
            mongoMappingContext
        )

        // "_class" 필드 제거
        converter.setTypeMapper(DefaultMongoTypeMapper(null))

        // 커스텀 Converter 설정
        converter.setCustomConversions(mongoCustomConversions)
        converter.afterPropertiesSet()

        return MongoTemplate(mongoDatabaseFactory, converter)
    }

    /**
     * 인덱스 초기화 컴포넌트를 별도 Bean으로 분리
     */
    @Bean
    fun mongoIndexInitializer(mongoTemplate: MongoTemplate): MongoIndexInitializer {
        return MongoIndexInitializer(mongoTemplate)
    }
}

/**
 * LocalDateTime을 MongoDB의 Date로 변환
 *
 * MongoDB는 날짜를 UTC Date 형식으로 저장
 */
@WritingConverter
class LocalDateTimeToDateConverter : Converter<LocalDateTime, Date> {
    override fun convert(source: LocalDateTime): Date {
        return Date.from(source.atZone(ZoneId.systemDefault()).toInstant())
    }
}

/**
 * MongoDB의 Date를 LocalDateTime으로 변환
 *
 * 조회 시 LocalDateTime으로 자동 변환
 */
@ReadingConverter
class DateToLocalDateTimeConverter : Converter<Date, LocalDateTime> {
    override fun convert(source: Date): LocalDateTime {
        return source.toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
}

/**
 * MongoDB 인덱스 초기화 전담 클래스
 * 
 * full_text_data 컬렉션의 인덱스를 자동 생성
 */
class MongoIndexInitializer(
    private val mongoTemplate: MongoTemplate
) {

    companion object {
        private val logger = KotlinLogging.logger { }
        private const val COLLECTION_NAME = "full_text_data"
        private val TTL_DURATION = Duration.ofDays(30)
    }

    @PostConstruct
    fun initIndexes() {
        try {
            logger.info { "🔧 MongoDB 인덱스 초기화 시작 (full_text_data)..." }

            val indexOps = mongoTemplate.indexOps(COLLECTION_NAME)

            // 1. refinedId 유니크 인덱스 (주요 조회 키)
            runCatching {
                indexOps.createIndex(
                    Index()
                        .on("refinedId", Sort.Direction.ASC)
                        .unique()
                        .named("refinedId_unique_idx")
                )
                logger.info { "  ✅ refinedId 유니크 인덱스 생성 완료" }
            }.onFailure { e ->
                if (e.message?.contains("already exists") == true) {
                    logger.info { "  ℹ️  refinedId 인덱스 이미 존재" }
                } else {
                    logger.warn(e) { "  ⚠️  refinedId 인덱스 생성 실패" }
                }
            }

            // 2. originId 인덱스 (원본 데이터 참조)
            runCatching {
                indexOps.createIndex(
                    Index()
                        .on("originId", Sort.Direction.ASC)
                        .named("originId_idx")
                )
                logger.info { "  ✅ originId 인덱스 생성 완료" }
            }.onFailure { e ->
                if (e.message?.contains("already exists") == true) {
                    logger.info { "  ℹ️  originId 인덱스 이미 존재" }
                } else {
                    logger.warn(e) { "  ⚠️  originId 인덱스 생성 실패" }
                }
            }

            // 3. sourceType 단일 인덱스
            runCatching {
                indexOps.createIndex(
                    Index()
                        .on("sourceType", Sort.Direction.ASC)
                        .named("sourceType_idx")
                )
                logger.info { "  ✅ sourceType 인덱스 생성 완료" }
            }.onFailure { e ->
                if (e.message?.contains("already exists") == true) {
                    logger.info { "  ℹ️  sourceType 인덱스 이미 존재" }
                } else {
                    logger.warn(e) { "  ⚠️  sourceType 인덱스 생성 실패" }
                }
            }

            // 4. TTL 인덱스 (30일 후 자동 삭제)
            runCatching {
                indexOps.createIndex(
                    Index()
                        .on("createdAt", Sort.Direction.ASC)
                        .expire(TTL_DURATION)
                        .named("createdAt_ttl_idx")
                )
                logger.info { "  ✅ TTL 인덱스 생성 완료 (30일 후 자동 삭제)" }
            }.onFailure { e ->
                if (e.message?.contains("already exists") == true) {
                    logger.info { "  ℹ️  TTL 인덱스 이미 존재" }
                } else {
                    logger.warn(e) { "  ⚠️  TTL 인덱스 생성 실패" }
                }
            }

            // 5. 복합 인덱스: sourceType + createdAt
            runCatching {
                indexOps.createIndex(
                    Index()
                        .on("sourceType", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .named("sourceType_createdAt_idx")
                )
                logger.info { "  ✅ sourceType + createdAt 복합 인덱스 생성 완료" }
            }.onFailure { e ->
                if (e.message?.contains("already exists") == true) {
                    logger.info { "  ℹ️  sourceType + createdAt 인덱스 이미 존재" }
                } else {
                    logger.warn(e) { "  ⚠️  sourceType + createdAt 인덱스 생성 실패" }
                }
            }

            logger.info { "🎉 MongoDB 인덱스 초기화 완료 (full_text_data)!" }

        } catch (e: Exception) {
            logger.error(e) { "❌ MongoDB 인덱스 생성 중 오류 발생" }
        }
    }
}
