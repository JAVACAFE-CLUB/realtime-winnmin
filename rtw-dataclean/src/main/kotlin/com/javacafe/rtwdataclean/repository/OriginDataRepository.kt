package com.javacafe.rtwdataclean.repository

import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

/**
 * MongoDB의 origin_data 컬렉션에서 데이터를 조회
 * 
 * rtw-collector가 생성한 원본 데이터를 조회
 */
@Repository
class OriginDataRepository(
    private val mongoTemplate: MongoTemplate
) {
    /**
     * ID로 원본 데이터 조회
     * 
     * @param id 원본 데이터 ID
     * @return Map 형태의 원본 데이터 (data 필드 포함)
     */
    fun findDataById(id: String): Map<String, Any>? {
        val query = Query.query(Criteria.where("_id").`is`(id))
        query.fields()
            .include("data")
            .include("source")
            .include("dataSize")
            .include("checksum")
        
        val document = mongoTemplate.findOne(query, Map::class.java, "origin_data")
        
        @Suppress("UNCHECKED_CAST")
        return document as? Map<String, Any>
    }
}
