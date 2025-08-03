package com.javacafe.realtimewinnmin.application.dto

import com.javacafe.realtimewinnmin.domain.news.entity.NewsArticle
import java.time.LocalDateTime

data class NewsCreateRequest(
    val title: String,
    val content: String? = null,
    val source: String? = "manual",
    val url: String? = null,
    val category: String? = null
) {
    fun toEntity(): NewsArticle {
        return NewsArticle.toEntity(
            title = title,
            content = content ?: title,
            source = source ?: "manual",
            url = url,
            category = category
        )
    }
}

data class NewsResponse(
    val id: String,
    val title: String,
    val content: String,
    val source: String,
    val createdAt: LocalDateTime,
    val url: String?,
    val category: String?
) {
    companion object {
        fun from(newsArticle: NewsArticle): NewsResponse {
            return NewsResponse(
                id = newsArticle.id,
                title = newsArticle.title,
                content = newsArticle.content,
                source = newsArticle.source,
                createdAt = newsArticle.createdAt,
                url = newsArticle.url,
                category = newsArticle.category
            )
        }
    }
}

data class NewsSearchRequest(
    val keyword: String,
    val source: String? = null,
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val size: Int = 10
)

data class NewsListResponse(
    val news: List<NewsResponse>,
    val totalCount: Long,
    val hasNext: Boolean
)
