package com.javacafe.realtimewinnmin.service

import com.javacafe.realtimewinnmin.application.dto.*
import com.javacafe.realtimewinnmin.application.service.NewsService
import com.javacafe.realtimewinnmin.common.exception.ExceptionCode
import com.javacafe.realtimewinnmin.common.exception.GlobalException
import com.javacafe.realtimewinnmin.domain.news.entity.NewsArticle
import com.javacafe.realtimewinnmin.domain.news.repository.NewsRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 🧪 NewsService 테스트
 *
 * JUnit 5 + Mockito + Spring Boot Test 조합으로 작성
 * 각 메서드별 성공/실패 케이스 각 1개씩 테스트
 */
@ExtendWith(MockitoExtension::class)
class NewsServiceTest {

    @Mock
    private lateinit var newsRepository: NewsRepository

    @InjectMocks
    private lateinit var newsService: NewsService

    @Test
    fun `searchNewsWithLimit 성공 - 키워드로 뉴스를 찾아서 결과 반환`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "삼성",
            size = 10
        )

        val mockNewsArticle = NewsArticle(
            title = "삼성전자 실적 발표",
            content = "삼성전자가 좋은 실적을 발표했습니다",
            source = "manual"
        )

        whenever(newsRepository.findTopByKeywordOrderByCreatedAtDesc(eq("삼성"), eq(10)))
            .thenReturn(listOf(mockNewsArticle))

        // when
        val result = newsService.searchNewsWithLimit(searchRequest)

        // then
        assertEquals(1, result.size)
        assertEquals("삼성전자 실적 발표", result[0].title)
        assertEquals("삼성전자가 좋은 실적을 발표했습니다", result[0].content)
        assertEquals("manual", result[0].source)

        verify(newsRepository, times(1)).findTopByKeywordOrderByCreatedAtDesc(eq("삼성"), eq(10))
    }

    @Test
    fun `searchNewsWithLimit 성공 - 빈 결과 반환`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "존재하지않는키워드",
            size = 10
        )

        whenever(newsRepository.findTopByKeywordOrderByCreatedAtDesc(eq("존재하지않는키워드"), eq(10)))
            .thenReturn(emptyList())

        // when
        val result = newsService.searchNewsWithLimit(searchRequest)

        // then
        assertTrue(result.isEmpty())

        verify(newsRepository, times(1)).findTopByKeywordOrderByCreatedAtDesc(eq("존재하지않는키워드"), eq(10))
    }

    @Test
    fun `searchNewsWithLimit 성공 - 여러 뉴스 결과 반환`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "삼성",
            size = 5
        )

        val mockNewsArticles = listOf(
            NewsArticle(
                title = "삼성전자 실적 발표",
                content = "삼성전자가 좋은 실적을 발표했습니다",
                source = "manual"
            ),
            NewsArticle(
                title = "삼성 신제품 출시",
                content = "삼성에서 새로운 제품을 출시했습니다",
                source = "auto"
            )
        )

        whenever(newsRepository.findTopByKeywordOrderByCreatedAtDesc(eq("삼성"), eq(5)))
            .thenReturn(mockNewsArticles)

        // when
        val result = newsService.searchNewsWithLimit(searchRequest)

        // then
        assertEquals(2, result.size)
        assertEquals("삼성전자 실적 발표", result[0].title)
        assertEquals("삼성 신제품 출시", result[1].title)

        verify(newsRepository, times(1)).findTopByKeywordOrderByCreatedAtDesc(eq("삼성"), eq(5))
    }

    @Test
    fun `searchNewsWithLimit 실패 - Repository에서 예외 발생 시 GlobalException 발생`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "삼성",
            size = 10
        )

        whenever(newsRepository.findTopByKeywordOrderByCreatedAtDesc(eq("삼성"), eq(10)))
            .thenThrow(RuntimeException("Repository error"))

        // when & then
        val exception = assertFailsWith<GlobalException> {
            newsService.searchNewsWithLimit(searchRequest)
        }

        assertEquals(ExceptionCode.INTERNAL_SERVER_ERROR, exception.exceptionCode)
        assertTrue(exception.message!!.contains("뉴스 검색 중 오류가 발생했습니다"))

        verify(newsRepository, times(1)).findTopByKeywordOrderByCreatedAtDesc(eq("삼성"), eq(10))
    }

    @Test
    fun `searchNewsWithLimit 성공 - size 파라미터 정확히 전달`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "테스트",
            size = 20
        )

        whenever(newsRepository.findTopByKeywordOrderByCreatedAtDesc(eq("테스트"), eq(20)))
            .thenReturn(emptyList())

        // when
        newsService.searchNewsWithLimit(searchRequest)

        // then
        verify(newsRepository, times(1)).findTopByKeywordOrderByCreatedAtDesc(eq("테스트"), eq(20))
    }

    @Test
    fun `createNews 성공 - 뉴스 생성 요청을 받아서 저장 후 응답 반환`() {
        // given
        val createRequest = NewsCreateRequest(
            title = "새로운 뉴스",
            content = "뉴스 내용입니다",
            source = "manual"
        )

        val savedNewsArticle = NewsArticle(
            title = "새로운 뉴스",
            content = "뉴스 내용입니다",
            source = "manual"
        )

        whenever(newsRepository.save(any<NewsArticle>()))
            .thenReturn(savedNewsArticle)

        // when
        val result = newsService.createNews(createRequest)

        // then
        assertEquals("새로운 뉴스", result.title)
        assertEquals("뉴스 내용입니다", result.content)
        assertEquals("manual", result.source)

        verify(newsRepository, times(1)).save(any<NewsArticle>())
    }

    @Test
    fun `createNews 실패 - Repository 저장 시 예외 발생하면 GlobalException 발생`() {
        // given
        val createRequest = NewsCreateRequest(
            title = "새로운 뉴스",
            content = "뉴스 내용입니다",
            source = "manual"
        )

        whenever(newsRepository.save(any<NewsArticle>()))
            .thenThrow(RuntimeException("Save error"))

        // when & then
        val exception = assertFailsWith<GlobalException> {
            newsService.createNews(createRequest)
        }

        assertEquals(ExceptionCode.INTERNAL_SERVER_ERROR, exception.exceptionCode)
        assertTrue(exception.message!!.contains("뉴스 기사 저장 중 오류가 발생했습니다"))

        verify(newsRepository, times(1)).save(any<NewsArticle>())
    }

    @Test
    fun `deleteNews 성공 - 존재하는 ID로 삭제 성공`() {
        // given
        val newsId = "existing-news-id"

        whenever(newsRepository.existsById(newsId)).thenReturn(true)
        doNothing().whenever(newsRepository).deleteById(newsId)

        // when
        newsService.deleteNews(newsId) // void 반환이므로 결과 값 없음

        // then
        verify(newsRepository, times(1)).existsById(newsId)
        verify(newsRepository, times(1)).deleteById(newsId)
    }

    @Test
    fun `deleteNews 실패 - 존재하지 않는 ID로 삭제 시 GlobalException 발생`() {
        // given
        val nonExistentId = "non-existent-id"

        whenever(newsRepository.existsById(nonExistentId)).thenReturn(false)

        // when & then
        val exception = assertFailsWith<GlobalException> {
            newsService.deleteNews(nonExistentId)
        }

        assertEquals(ExceptionCode.NOT_FOUND_ERROR, exception.exceptionCode)
        assertTrue(exception.message!!.contains("삭제할 뉴스 기사를 찾을 수 없습니다"))

        verify(newsRepository, times(1)).existsById(nonExistentId)
        verify(newsRepository, never()).deleteById(any())
    }

    @Test
    fun `deleteNews 실패 - Repository 삭제 시 예외 발생하면 GlobalException 발생`() {
        // given
        val newsId = "existing-news-id"

        whenever(newsRepository.existsById(newsId)).thenReturn(true)
        whenever(newsRepository.deleteById(newsId)).thenThrow(RuntimeException("Delete error"))

        // when & then
        val exception = assertFailsWith<GlobalException> {
            newsService.deleteNews(newsId)
        }

        assertEquals(ExceptionCode.INTERNAL_SERVER_ERROR, exception.exceptionCode)
        assertTrue(exception.message!!.contains("뉴스 기사 삭제 중 오류가 발생했습니다"))

        verify(newsRepository, times(1)).existsById(newsId)
        verify(newsRepository, times(1)).deleteById(newsId)
    }
}
