package com.javacafe.rtwserve.application.service

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.search.Highlight
import co.elastic.clients.elasticsearch.core.search.HighlightField
import co.elastic.clients.json.JsonData
import com.javacafe.rtwcore.model.es.ArticleDocument
import com.javacafe.rtwserve.application.dto.ArticleDto
import com.javacafe.rtwserve.application.dto.AutocompleteResponse
import com.javacafe.rtwserve.application.dto.SearchResponse
import com.javacafe.rtwserve.config.ServeProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import com.javacafe.rtwserve.application.dto.SearchRequest as SearchRequestDto

private val logger = KotlinLogging.logger {}

/**
 * 검색 서비스
 * 
 * Elasticsearch를 활용한 문서 검색
 */
@Service
class SearchService(
    private val elasticsearchClient: ElasticsearchClient,
    private val serveProperties: ServeProperties
) {
    private val articleIndex: String
        get() = serveProperties.elasticsearch.articleIndex

    /**
     * 통합 검색
     */
    fun search(request: SearchRequestDto): SearchResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            // 페이지 사이즈 제한
            val size = minOf(request.size, serveProperties.search.maxPageSize)
            val from = request.page * size

            // Bool Query 구성
            val boolQuery = buildBoolQuery(request)

            // 하이라이트 설정
            val highlight = buildHighlight()

            // 검색 요청 구성
            val searchRequestBuilder = SearchRequest.Builder()
                .index(articleIndex)
                .query(Query.Builder().bool(boolQuery).build())
                .from(from)
                .size(size)
                .highlight(highlight)

            // 정렬 적용
            when (request.sortBy.lowercase()) {
                "date" -> searchRequestBuilder.sort { s ->
                    s.field { f -> f.field("indexedAt").order(SortOrder.Desc) }
                }
                "score" -> searchRequestBuilder.sort { s ->
                    s.field { f -> f.field("_score").order(SortOrder.Desc) }
                }
                else -> {
                    // relevance - 기본 스코어 정렬
                }
            }

            val searchRequest = searchRequestBuilder.build()
            val response = elasticsearchClient.search(searchRequest, ArticleDocument::class.java)

            val totalHits = response.hits().total()?.value() ?: 0
            val articles = response.hits().hits().mapNotNull { hit ->
                hit.source()?.let { doc ->
                    val highlightedContent = hit.highlight()["fullText"]?.firstOrNull()
                        ?: hit.highlight()["title"]?.firstOrNull()
                        ?: doc.fullText?.take(200) ?: ""

                    (hit.highlight()["title"]?.firstOrNull() ?: doc.title)?.let {
                        ArticleDto(
                            id = doc.refinedId ?: "",
                            title = it,
                            content = highlightedContent,
                            author = doc.author ?: "",
                            source = doc.source ?: "",
                            sourceType = doc.sourceType ?: "",
                            category = doc.category,
                            url = doc.url,
                            publishedAt = doc.publishedAt,
                            indexedAt = doc.indexedAt,
                            keywords = doc.keywords,
                            score = hit.score()?.toFloat()
                        )
                    }
                }
            }

            val took = System.currentTimeMillis() - startTime
            logger.info { "Search completed: query='${request.query}', hits=$totalHits, took=${took}ms" }

            return SearchResponse(
                query = request.query,
                totalHits = totalHits,
                page = request.page,
                size = size,
                totalPages = ((totalHits + size - 1) / size).toInt(),
                articles = articles,
                took = took
            )

        } catch (e: Exception) {
            logger.error(e) { "Search failed: query='${request.query}'" }
            throw RuntimeException("검색 중 오류가 발생했습니다.", e)
        }
    }

    /**
     * 문서 상세 조회
     */
    fun getArticle(id: String): ArticleDto? {
        return try {
            val response = elasticsearchClient.get({ g ->
                g.index(articleIndex).id(id)
            }, ArticleDocument::class.java)

            response.source()?.let { doc ->
                ArticleDto(
                    id = doc.refinedId ?: "",
                    title = doc.title ?: "",
                    content = doc.fullText ?: "",
                    author = doc.author ?: "",
                    source = doc.source ?: "",
                    sourceType = doc.sourceType ?: "",
                    category = doc.category,
                    url = doc.url,
                    publishedAt = doc.publishedAt,
                    indexedAt = doc.indexedAt,
                    keywords = doc.keywords,
                    score = null
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get article: id=$id" }
            null
        }
    }

    /**
     * 최신 문서 조회
     */
    fun getRecentArticles(size: Int = 20, sourceType: String? = null): List<ArticleDto> {
        try {
            val searchRequestBuilder = SearchRequest.Builder()
                .index(articleIndex)
                .size(minOf(size, serveProperties.search.maxPageSize))
                .sort { s -> s.field { f -> f.field("indexedAt").order(SortOrder.Desc) } }

            if (sourceType != null) {
                searchRequestBuilder.query { q ->
                    q.term { t -> t.field("sourceType").value(sourceType) }
                }
            }

            val response = elasticsearchClient.search(searchRequestBuilder.build(), ArticleDocument::class.java)

            return response.hits().hits().mapNotNull { hit ->
                hit.source()?.let { doc ->
                    ArticleDto(
                        id = doc.refinedId ?: "",
                        title = doc.title ?: "",
                        content = doc.fullText?.take(200) ?: "",
                        author = doc.author ?: "",
                        source = doc.source ?: "",
                        sourceType = doc.sourceType ?: "",
                        category = doc.category,
                        url = doc.url,
                        publishedAt = doc.publishedAt,
                        indexedAt = doc.indexedAt,
                        keywords = doc.keywords,
                        score = null
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get recent articles" }
            return emptyList()
        }
    }

    /**
     * 키워드로 문서 검색
     */
    fun searchByKeyword(keyword: String, page: Int = 0, size: Int = 20): SearchResponse {
        val request = SearchRequestDto(
            query = keyword,
            page = page,
            size = size
        )
        return search(request)
    }

    /**
     * 자동완성 제안
     */
    fun autocomplete(prefix: String, size: Int = 10): AutocompleteResponse {
        try {
            val searchRequest = SearchRequest.Builder()
                .index(articleIndex)
                .size(size)
                .query { q ->
                    q.matchPhrasePrefix { m ->
                        m.field("title").query(prefix)
                    }
                }
                .source { s -> s.filter { f -> f.includes("title") } }
                .build()

            val response = elasticsearchClient.search(searchRequest, ArticleDocument::class.java)

            val suggestions = response.hits().hits()
                .mapNotNull { it.source()?.title }
                .distinct()
                .take(size)

            return AutocompleteResponse(
                query = prefix,
                suggestions = suggestions
            )
        } catch (e: Exception) {
            logger.error(e) { "Autocomplete failed: prefix=$prefix" }
            return AutocompleteResponse(prefix, emptyList())
        }
    }

    /**
     * Bool Query 구성
     */
    private fun buildBoolQuery(request: SearchRequestDto): BoolQuery {
        val boolBuilder = BoolQuery.Builder()

        // 메인 검색 쿼리 (title, fullText)
        if (request.query.isNotBlank()) {
            boolBuilder.must { m ->
                m.multiMatch { mm ->
                    mm.query(request.query)
                        .fields("title^3", "fullText", "keywords^2")
                        .fuzziness("AUTO")
                }
            }
        }

        // 소스 타입 필터
        request.sourceType?.let { sourceType ->
            boolBuilder.filter { f ->
                f.term { t -> t.field("sourceType").value(sourceType) }
            }
        }

        // 카테고리 필터
        request.category?.let { category ->
            boolBuilder.filter { f ->
                f.term { t -> t.field("category").value(category) }
            }
        }

        // 날짜 범위 필터
        if (request.fromDate != null || request.toDate != null) {
            boolBuilder.filter { f ->
                f.range { r ->
                    val rangeBuilder = r.field("indexedAt")
                    request.fromDate?.let { 
                        rangeBuilder.gte(JsonData.of(it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                    }
                    request.toDate?.let {
                        rangeBuilder.lte(JsonData.of(it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                    }
                    rangeBuilder
                }
            }
        }

        return boolBuilder.build()
    }

    /**
     * 하이라이트 설정
     */
    private fun buildHighlight(): Highlight {
        return Highlight.Builder()
            .preTags("<em>")
            .postTags("</em>")
            .fields("title", HighlightField.Builder().build())
            .fields("fullText", HighlightField.Builder()
                .fragmentSize(serveProperties.search.highlightFragmentSize)
                .numberOfFragments(3)
                .build())
            .build()
    }
}
