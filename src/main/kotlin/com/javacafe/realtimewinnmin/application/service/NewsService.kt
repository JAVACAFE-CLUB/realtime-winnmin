package com.javacafe.realtimewinnmin.application.service

import com.javacafe.realtimewinnmin.application.dto.*
import com.javacafe.realtimewinnmin.domain.news.repository.NewsRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class NewsService(
    private val newsRepository: NewsRepository,
) {
    private val logger = KotlinLogging.logger { }

    /**
     * 키워드로 뉴스 검색
     */
    fun searchNews(request: NewsSearchRequest): NewsListResponse {
        logger.info { "Searching news with keyword: ${request.keyword}" }

        val pageable = PageRequest.of(
            0,
            request.size,
            Sort.by(Sort.Direction.DESC, "createdAt")
        )
        val searchResults = newsRepository.searchByKeyword(request.keyword, pageable)

        logger.info { "Found ${searchResults.size} news articles for keyword: ${request.keyword}" }

        return NewsListResponse(
            news = searchResults.map(NewsResponse::from),
            totalCount = searchResults.size.toLong(),
            hasNext = searchResults.size >= request.size
        )
    }

    /**
     * 뉴스 기사 저장
     */
    fun createNews(request: NewsCreateRequest): NewsResponse {
        logger.info { "Creating news article: ${request.title}" }

        val newsArticle = request.toEntity()
        val savedArticle = newsRepository.save(newsArticle)

        logger.info { "News article created with ID: ${savedArticle.id}" }
        return NewsResponse.from(savedArticle)
    }

    /**
     * 뉴스 기사 삭제
     */
    fun deleteNews(id: String): Boolean =
        newsRepository.existsById(id).also { exists ->
            if (exists) {
                newsRepository.deleteById(id)
                logger.info { "News article deleted: $id" }
            } else {
                logger.warn { "News article not found for deletion: $id" }
            }
        }
}