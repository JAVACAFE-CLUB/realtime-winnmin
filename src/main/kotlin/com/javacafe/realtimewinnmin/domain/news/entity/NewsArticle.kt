package com.javacafe.realtimewinnmin.domain.news.entity

import com.javacafe.realtimewinnmin.domain.common.ImmutableEntity
import org.springframework.data.elasticsearch.annotations.*

@Document(indexName = "news_articles")
data class NewsArticle(
    @Field(type = FieldType.Text, analyzer = "nori")
    val title: String,
    
    @Field(type = FieldType.Text, analyzer = "nori")
    val content: String,
    
    @Field(type = FieldType.Keyword)
    val source: String,
    
    @Field(type = FieldType.Keyword)
    val url: String? = null,
    
    @Field(type = FieldType.Keyword)
    val category: String? = null
) : ImmutableEntity() {
    
    companion object {
        fun toEntity(
            title: String,
            content: String,
            source: String,
            url: String? = null,
            category: String? = null,
        ): NewsArticle {
            return NewsArticle(
                title = title,
                content = content.ifEmpty { title },
                source = source,
                url = url,
                category = category
            )
        }
    }
}
