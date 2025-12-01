package com.javacafe.rtwindex.repository

import com.javacafe.rtwcore.model.mongodb.FullTextDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

/**
 * MongoDB full_text_data 컬렉션 Repository
 * 
 * rtw-dataclean에서 저장한 풀텍스트 데이터를 조회
 */
@Repository
interface FullTextRepository : MongoRepository<FullTextDocument, String> {
    
    /**
     * refinedId로 단일 문서 조회
     */
    fun findByRefinedId(refinedId: String): FullTextDocument?
    
    /**
     * refinedId 목록으로 배치 조회
     * 
     * 성능을 위해 IN 쿼리 사용
     */
    fun findByRefinedIdIn(refinedIds: List<String>): List<FullTextDocument>
    
    /**
     * sourceType으로 조회
     */
    fun findBySourceType(sourceType: String): List<FullTextDocument>
    
    /**
     * 추출 성공한 문서만 조회
     */
    fun findByExtractionSuccessTrue(): List<FullTextDocument>
}
