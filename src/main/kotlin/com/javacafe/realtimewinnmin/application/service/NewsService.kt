package com.javacafe.realtimewinnmin.application.service

import com.javacafe.realtimewinnmin.application.dto.*
import com.javacafe.realtimewinnmin.common.exception.ExceptionCode
import com.javacafe.realtimewinnmin.common.exception.GlobalException
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

    fun searchNewsWithLimit(request: NewsSearchRequest): List<NewsResponse> {
        logger.info { "searchNewsWithLimit 검색 keyword: ${request.keyword}" }

        return runCatching {
            val sort = Sort.by(Sort.Direction.DESC, "createdAt")
            val pageable = PageRequest.of(0, request.size, sort)
            val searchResults = newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
                title = request.keyword,
                content = request.keyword,
                pageable = pageable
            )
            searchResults.content.map(NewsResponse::from)
        }.fold(
            onSuccess = { results ->
                logger.info { "[keyword]: ${request.keyword}에 대한 뉴스기사 ${results.size}개 검색됨" }
                results
            },
            onFailure = { exception ->
                logger.error { "Failed to search news with keyword: ${request.keyword} - ${exception.message}" }
                throw GlobalException(
                    exceptionCode = ExceptionCode.INTERNAL_SERVER_ERROR,
                    message = "뉴스 검색 중 오류가 발생했습니다. ${exception.message}"
                )
            }
        )
    }

    fun createNews(request: NewsCreateRequest): NewsResponse {
        logger.info { "NewsArticle 저징 시작 : [제목] ${request.title}" }

        return runCatching {
            val newsArticle = request.toEntity()
            val savedArticle = newsRepository.save(newsArticle)
            NewsResponse.from(savedArticle)
        }.fold(
            onSuccess = { newsResponse ->
                logger.info { "저정된 NewsArticle Id : ${newsResponse.id}" }
                newsResponse
            },
            onFailure = { exception ->
                logger.error(exception) { "NewsArticle 저장 실패 : [제목] ${request.title}" }
                throw GlobalException(
                    exceptionCode = ExceptionCode.INTERNAL_SERVER_ERROR,
                    message = "뉴스 기사 저장 중 오류가 발생했습니다. ${exception.message}"
                )
            }
        )
    }

    fun deleteNews(id: String) {
        logger.info { "NewsArticle 삭제 시작 Id : $id" }

        return runCatching {
            if (!newsRepository.existsById(id)) {
                logger.warn { "NewsArticle Id[$id] 삭제 실패 - 존재하지 않는 Id" }
                throw GlobalException(
                    exceptionCode = ExceptionCode.NOT_FOUND_ERROR,
                    message = "삭제할 뉴스 기사를 찾을 수 없습니다. ID: $id"
                )
            }

            newsRepository.deleteById(id)
            logger.info { "NewsArticle Id[$id] 삭제 성공" }
        }.getOrElse { exception ->
            when (exception) {
                is GlobalException -> throw exception
                else -> {
                    logger.error(exception) { "F뉴스 기사 삭제 중 오류 발생 [ID]: $id" }
                    throw GlobalException(
                        exceptionCode = ExceptionCode.INTERNAL_SERVER_ERROR,
                        message = "뉴스 기사 삭제 중 오류가 발생했습니다. ${exception.message}"
                    )
                }
            }
        }
    }
}
