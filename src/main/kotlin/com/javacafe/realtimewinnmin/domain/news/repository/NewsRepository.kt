package com.javacafe.realtimewinnmin.domain.news.repository

import com.javacafe.realtimewinnmin.domain.news.entity.NewsArticle
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface NewsRepository : ElasticsearchRepository<NewsArticle, String> {
    /**
     * 커스텀 검색 쿼리 - 제목과 내용에서 키워드 검색
     */
    @Query("""
        {
            "bool": {
                "should": [
                    {"match": {"title": "?0"}},
                    {"match": {"content": "?0"}}
                ],
                "minimum_should_match": 1
            }
        }
    """)
    fun searchByKeyword(keyword: String, pageable: Pageable): List<NewsArticle>
}
