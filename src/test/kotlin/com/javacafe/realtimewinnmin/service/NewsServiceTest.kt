package com.javacafe.realtimewinnmin.service

import com.javacafe.realtimewinnmin.application.dto.*
import com.javacafe.realtimewinnmin.application.service.NewsService
import com.javacafe.realtimewinnmin.domain.news.entity.NewsArticle
import com.javacafe.realtimewinnmin.domain.news.repository.NewsRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.Pageable
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `searchNews 성공 - 키워드로 뉴스를 찾아서 결과 반환`() {
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
        
        whenever(newsRepository.searchByKeyword(eq("삼성"), any<Pageable>()))
            .thenReturn(listOf(mockNewsArticle))
        
        // when
        val result = newsService.searchNews(searchRequest)
        
        // then
        assertEquals(1, result.news.size)
        assertEquals(1, result.totalCount)
        assertFalse(result.hasNext)
        assertEquals("삼성전자 실적 발표", result.news[0].title)
        
        verify(newsRepository, times(1)).searchByKeyword(eq("삼성"), any<Pageable>())
    }
    
    @Test
    fun `searchNews 실패 - Repository에서 예외 발생 시 전파`() {
        // given
        val searchRequest = NewsSearchRequest(
            keyword = "삼성",
            size = 10
        )
        
        whenever(newsRepository.searchByKeyword(eq("삼성"), any<Pageable>()))
            .thenThrow(RuntimeException("Repository error"))
        
        // when & then
        assertFailsWith<RuntimeException> {
            newsService.searchNews(searchRequest)
        }
        
        verify(newsRepository, times(1)).searchByKeyword(eq("삼성"), any<Pageable>())
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
    fun `createNews 실패 - Repository 저장 시 예외 발생`() {
        // given
        val createRequest = NewsCreateRequest(
            title = "새로운 뉴스",
            content = "뉴스 내용입니다",
            source = "manual"
        )
        
        whenever(newsRepository.save(any<NewsArticle>()))
            .thenThrow(RuntimeException("Save error"))
        
        // when & then
        assertFailsWith<RuntimeException> {
            newsService.createNews(createRequest)
        }
        
        verify(newsRepository, times(1)).save(any<NewsArticle>())
    }
    
    @Test
    fun `deleteNews 성공 - 존재하는 ID로 삭제 성공 후 true 반환`() {
        // given
        val newsId = "existing-news-id"
        
        whenever(newsRepository.existsById(newsId)).thenReturn(true)
        doNothing().whenever(newsRepository).deleteById(newsId)
        
        // when
        val result = newsService.deleteNews(newsId)
        
        // then
        assertTrue(result)
        
        verify(newsRepository, times(1)).existsById(newsId)
        verify(newsRepository, times(1)).deleteById(newsId)
    }
    
    @Test
    fun `deleteNews 실패 - 존재하지 않는 ID로 삭제 실패 후 false 반환`() {
        // given
        val nonExistentId = "non-existent-id"
        
        whenever(newsRepository.existsById(nonExistentId)).thenReturn(false)
        
        // when
        val result = newsService.deleteNews(nonExistentId)
        
        // then
        assertFalse(result)
        
        verify(newsRepository, times(1)).existsById(nonExistentId)
        verify(newsRepository, never()).deleteById(any())
    }
}
