package com.javacafe.rtwserve.service

import com.javacafe.rtwserve.application.dto.*
import com.javacafe.rtwserve.application.service.NewsService
import com.javacafe.rtwserve.common.exception.ExceptionCode
import com.javacafe.rtwserve.common.exception.GlobalException
import com.javacafe.rtwserve.domain.news.entity.NewsArticle
import com.javacafe.rtwserve.domain.news.repository.NewsRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 🧪 NewsService 테스트
 *
 * JUnit 5 + Mockito + Spring Boot Test 조합으로 작성
 * 실제 비즈니스 로직에 대한 적절한 테스트
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

        // NewsArticle은 불변 엔티티이므로 데이터 클래스로 생성
        val mockNewsArticle = NewsArticle(
            title = "삼성전자 실적 발표",
            content = "삼성전자가 좋은 실적을 발표했습니다",
            source = "manual",
            url = "https://example.com",
            category = "business"
        )

        val mockPage = PageImpl(
            listOf(mockNewsArticle),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")),
            1
        )

        whenever(newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("삼성"), eq("삼성"), any<Pageable>()
        )).thenReturn(mockPage)

        // when
        val result = newsService.searchNewsWithLimit(searchRequest)

        // then
        assertEquals(1, result.size)
        assertEquals("삼성전자 실적 발표", result[0].title)
        assertEquals("삼성전자가 좋은 실적을 발표했습니다", result[0].content)
        assertEquals("manual", result[0].source)
        assertEquals("https://example.com", result[0].url)
        assertEquals("business", result[0].category)

        verify(newsRepository, times(1)).findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("삼성"), eq("삼성"), any<Pageable>()
        )
    }

    @Test
    fun `searchNewsWithLimit 성공 - 빈 결과 반환`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "존재하지않는키워드",
            size = 10
        )

        val emptyPage = PageImpl<NewsArticle>(
            emptyList(),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")),
            0
        )

        whenever(newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("존재하지않는키워드"), eq("존재하지않는키워드"), any<Pageable>()
        )).thenReturn(emptyPage)

        // when
        val result = newsService.searchNewsWithLimit(searchRequest)

        // then
        assertTrue(result.isEmpty())

        verify(newsRepository, times(1)).findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("존재하지않는키워드"), eq("존재하지않는키워드"), any<Pageable>()
        )
    }

    @Test
    fun `searchNewsWithLimit 성공 - 여러 뉴스 결과 반환 및 Pageable 설정 확인`() {
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

        val mockPage = PageImpl(
            mockNewsArticles,
            PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")),
            2
        )

        whenever(newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("삼성"), eq("삼성"), any<Pageable>()
        )).thenReturn(mockPage)

        // when
        val result = newsService.searchNewsWithLimit(searchRequest)

        // then
        assertEquals(2, result.size)
        assertEquals("삼성전자 실적 발표", result[0].title)
        assertEquals("삼성 신제품 출시", result[1].title)

        // Pageable 설정 검증
        argumentCaptor<Pageable>().apply {
            verify(newsRepository).findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
                eq("삼성"), eq("삼성"), capture()
            )
            
            val capturedPageable = firstValue
            assertEquals(0, capturedPageable.pageNumber)
            assertEquals(5, capturedPageable.pageSize)
            assertEquals(Sort.Direction.DESC, capturedPageable.sort.getOrderFor("createdAt")?.direction)
        }
    }

    @Test
    fun `searchNewsWithLimit 실패 - Repository에서 예외 발생 시 GlobalException 발생`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "삼성",
            size = 10
        )

        whenever(newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("삼성"), eq("삼성"), any<Pageable>()
        )).thenThrow(RuntimeException("Repository error"))

        // when & then
        val exception = assertFailsWith<GlobalException> {
            newsService.searchNewsWithLimit(searchRequest)
        }

        assertEquals(ExceptionCode.INTERNAL_SERVER_ERROR, exception.exceptionCode)
        assertTrue(exception.message!!.contains("뉴스 검색 중 오류가 발생했습니다"))
        assertTrue(exception.message!!.contains("Repository error"))

        verify(newsRepository, times(1)).findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("삼성"), eq("삼성"), any<Pageable>()
        )
    }

    @Test
    fun `createNews 성공 - 뉴스 생성 요청을 받아서 저장 후 응답 반환`() {
        // given
        val createRequest = NewsCreateRequest(
            title = "새로운 뉴스",
            content = "뉴스 내용입니다",
            source = "manual",
            url = "https://test.com",
            category = "tech"
        )

        val savedNewsArticle = NewsArticle(
            title = "새로운 뉴스",
            content = "뉴스 내용입니다",
            source = "manual",
            url = "https://test.com",
            category = "tech"
        )

        whenever(newsRepository.save(any<NewsArticle>()))
            .thenReturn(savedNewsArticle)

        // when
        val result = newsService.createNews(createRequest)

        // then
        assertEquals("새로운 뉴스", result.title)
        assertEquals("뉴스 내용입니다", result.content)
        assertEquals("manual", result.source)
        assertEquals("https://test.com", result.url)
        assertEquals("tech", result.category)

        // save 호출 시 올바른 엔티티가 전달되었는지 검증
        argumentCaptor<NewsArticle>().apply {
            verify(newsRepository).save(capture())
            
            val capturedArticle = firstValue
            assertEquals("새로운 뉴스", capturedArticle.title)
            assertEquals("뉴스 내용입니다", capturedArticle.content)
            assertEquals("manual", capturedArticle.source)
            assertEquals("https://test.com", capturedArticle.url)
            assertEquals("tech", capturedArticle.category)
        }
    }

    @Test
    fun `createNews 성공 - content가 null인 경우 title로 대체`() {
        // given
        val createRequest = NewsCreateRequest(
            title = "제목만 있는 뉴스",
            content = null,
            source = "manual"
        )

        val savedNewsArticle = NewsArticle(
            title = "제목만 있는 뉴스",
            content = "제목만 있는 뉴스", // content가 null이면 title로 대체됨
            source = "manual"
        )

        whenever(newsRepository.save(any<NewsArticle>()))
            .thenReturn(savedNewsArticle)

        // when
        val result = newsService.createNews(createRequest)

        // then
        assertEquals("제목만 있는 뉴스", result.title)
        assertEquals("제목만 있는 뉴스", result.content) // title과 동일

        argumentCaptor<NewsArticle>().apply {
            verify(newsRepository).save(capture())
            val capturedArticle = firstValue
            assertEquals("제목만 있는 뉴스", capturedArticle.content)
        }
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
        assertTrue(exception.message!!.contains("Save error"))

        verify(newsRepository, times(1)).save(any<NewsArticle>())
    }

    @Test
    fun `deleteNews 성공 - 존재하는 ID로 삭제 성공`() {
        // given
        val newsId = "existing-news-id"

        whenever(newsRepository.existsById(newsId)).thenReturn(true)
        doNothing().whenever(newsRepository).deleteById(newsId)

        // when
        newsService.deleteNews(newsId)

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
        assertTrue(exception.message!!.contains(nonExistentId))

        verify(newsRepository, times(1)).existsById(nonExistentId)
        verify(newsRepository, never()).deleteById(any())
    }

    @Test
    fun `deleteNews 실패 - Repository 삭제 시 예외 발생하면 GlobalException 발생`() {
        // given
        val newsId = "existing-news-id"

        whenever(newsRepository.existsById(newsId)).thenReturn(true)
        doThrow(RuntimeException("Delete error")).whenever(newsRepository).deleteById(newsId)

        // when & then
        val exception = assertFailsWith<GlobalException> {
            newsService.deleteNews(newsId)
        }

        assertEquals(ExceptionCode.INTERNAL_SERVER_ERROR, exception.exceptionCode)
        assertTrue(exception.message!!.contains("뉴스 기사 삭제 중 오류가 발생했습니다"))
        assertTrue(exception.message!!.contains("Delete error"))

        verify(newsRepository, times(1)).existsById(newsId)
        verify(newsRepository, times(1)).deleteById(newsId)
    }

    @Test
    fun `searchNewsWithLimit - 다양한 size 파라미터에 대한 Pageable 설정 검증`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "테스트",
            size = 20
        )

        val emptyPage = PageImpl<NewsArticle>(
            emptyList(),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
            0
        )

        whenever(newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("테스트"), eq("테스트"), any<Pageable>()
        )).thenReturn(emptyPage)

        // when
        newsService.searchNewsWithLimit(searchRequest)

        // then
        argumentCaptor<Pageable>().apply {
            verify(newsRepository).findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
                eq("테스트"), eq("테스트"), capture()
            )
            
            val capturedPageable = firstValue
            assertEquals(20, capturedPageable.pageSize)
            assertEquals(0, capturedPageable.pageNumber)
        }
    }

    @Test
    fun `searchNewsWithLimit - 검색 키워드가 title과 content 모두에 전달되는지 확인`() {
        // given
        val searchRequest = NewsSearchRequest(keyword = "특별한키워드", size = 5)

        val emptyPage = PageImpl<NewsArticle>(emptyList())
        whenever(newsRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            any(), any(), any<Pageable>()
        )).thenReturn(emptyPage)

        // when
        newsService.searchNewsWithLimit(searchRequest)

        // then
        verify(newsRepository).findByTitleContainingOrContentContainingOrderByCreatedAtDesc(
            eq("특별한키워드"), 
            eq("특별한키워드"), 
            any<Pageable>()
        )
    }
}