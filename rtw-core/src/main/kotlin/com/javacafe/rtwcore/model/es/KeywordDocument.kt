package com.javacafe.rtwcore.model.es

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 일별 키워드 통계
 * 
 * Elasticsearch rtw-keywords 인덱스
 * rtw-index에서 집계/저장, rtw-serve에서 조회
 */
data class DailyKeyword(
    /**
     * 날짜
     */
    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val date: LocalDate,
    
    /**
     * 시간 (0-23, 시간별 집계 시 사용)
     */
    @JsonProperty("hour")
    val hour: Int? = null,
    
    /**
     * 키워드
     */
    @JsonProperty("keyword")
    val keyword: String,
    
    /**
     * 총 빈도수
     */
    @JsonProperty("frequency")
    val frequency: Long,
    
    /**
     * 등장 문서 수
     */
    @JsonProperty("documentCount")
    val documentCount: Int,
    
    /**
     * 점수 (가중치 적용)
     */
    @JsonProperty("score")
    val score: Float,
    
    /**
     * 순위
     */
    @JsonProperty("rank")
    val rank: Int,
    
    /**
     * 소스별 분포
     */
    @JsonProperty("sourceBreakdown")
    val sourceBreakdown: SourceBreakdown = SourceBreakdown(),
    
    /**
     * 시간별 추이
     */
    @JsonProperty("hourlyTrend")
    val hourlyTrend: List<HourlyCount> = emptyList(),
    
    /**
     * 이전 순위
     */
    @JsonProperty("previousRank")
    val previousRank: Int? = null,
    
    /**
     * 순위 변동 (양수: 상승, 음수: 하락)
     */
    @JsonProperty("rankChange")
    val rankChange: Int? = null,
    
    /**
     * 신규 진입 여부
     */
    @JsonProperty("isNew")
    val isNew: Boolean = false,
    
    /**
     * 생성 시각
     */
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    /**
     * 수정 시각
     */
    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now()
) : Serializable {
    
    /**
     * ES 문서 ID 생성
     */
    fun toDocumentId(): String {
        return if (hour != null) {
            "${date}_${hour}_${keyword}"
        } else {
            "${date}_${keyword}"
        }
    }
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * 소스별 분포
 */
data class SourceBreakdown(
    @JsonProperty("rss")
    val rss: Int = 0,
    
    @JsonProperty("wiki")
    val wiki: Int = 0,
    
    @JsonProperty("twitter")
    val twitter: Int = 0
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * 시간별 카운트
 */
data class HourlyCount(
    @JsonProperty("hour")
    val hour: Int,
    
    @JsonProperty("count")
    val count: Int
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Top 키워드 응답 (캐시용)
 */
data class TopKeywordsResponse(
    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    val date: LocalDate,
    
    @JsonProperty("keywords")
    val keywords: List<DailyKeyword>,
    
    @JsonProperty("generatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val generatedAt: LocalDateTime = LocalDateTime.now()
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
