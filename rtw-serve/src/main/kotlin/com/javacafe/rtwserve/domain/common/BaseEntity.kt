package com.javacafe.rtwserve.domain.common

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime
import java.util.*

sealed interface BaseEntity

/**
 * 추가 전용 엔티티 (생성 시간만 관리)
 */
abstract class ImmutableEntity : BaseEntity {
    
    @Id
    open val id: String = UUID.randomUUID().toString()
    
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
    open val createdAt: LocalDateTime = LocalDateTime.now()
}

/**
 * 수정 가능한 엔티티 (생성/수정 시간 모두 관리)
 */
abstract class MutableEntity : BaseEntity {
    
    @Id
    open val id: String = UUID.randomUUID().toString()
    
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
    open val createdAt: LocalDateTime = LocalDateTime.now()
    
    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
    open var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set
    
    /**
     * 엔티티 수정 시 호출하여 updatedAt 갱신
     */
    protected fun markAsUpdated() {
        updatedAt = LocalDateTime.now()
    }
}
