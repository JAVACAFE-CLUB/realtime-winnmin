package com.javacafe.rtwserving.domain.news.repository

import com.javacafe.rtwserving.domain.news.entity.NewsArticle
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsRepository : ElasticsearchRepository<NewsArticle, String> {

    fun findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
        title: String,
        content: String,
        pageable: Pageable
    ): Page<NewsArticle>
}
