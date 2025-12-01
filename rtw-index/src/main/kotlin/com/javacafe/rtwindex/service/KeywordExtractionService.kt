package com.javacafe.rtwindex.service

import com.javacafe.rtwindex.config.IndexProperties
import com.javacafe.rtwindex.model.KeywordScore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.math.ln
import kotlin.math.sqrt

private val logger = KotlinLogging.logger {}

/**
 * 키워드 추출 서비스
 * 
 * Elasticsearch Nori 분석기를 활용하여:
 * 1. 텍스트를 형태소 분석 (명사 추출)
 * 2. 빈도수 계산
 * 3. TF-IDF 기반 점수 부여
 * 
 * 점수 계산 방식:
 * - TF (Term Frequency): 문서 내 빈도수
 * - 정규화: 문서 길이에 따른 보정
 * - 가중치: 제목/본문 위치 가중치
 */
@Service
class KeywordExtractionService(
    private val morphemeAnalyzerService: MorphemeAnalyzerService,
    private val indexProperties: IndexProperties
) {
    private val minKeywordLength: Int
        get() = indexProperties.morpheme.minKeywordLength

    private val maxKeywordLength: Int
        get() = indexProperties.morpheme.maxKeywordLength

    private val maxKeywordsPerDoc: Int
        get() = indexProperties.morpheme.maxKeywordsPerDoc

    /**
     * 불용어 목록 (형태소 분석 후 추가 필터링)
     */
    private val stopwords = setOf(
        // 일반 불용어
        "것", "수", "등", "및", "이", "그", "저", "때", "더", "안",
        // 시간 관련
        "년", "월", "일", "시", "분", "초", "오늘", "내일", "어제",
        // 수량 관련  
        "개", "명", "건", "회", "번", "차", "만", "억", "조",
        // 일반 명사
        "경우", "때문", "이후", "이전", "현재", "최근", "관련", "대상",
        "부분", "전체", "일부", "대부분", "가운데", "사이", "이상", "이하",
        // 동작성 명사
        "진행", "실시", "발표", "공개", "제공", "사용", "활용", "적용"
    )

    /**
     * 텍스트에서 키워드 추출
     * 
     * @param text 분석할 텍스트
     * @param title 제목 (가중치 적용용, 선택)
     * @param maxKeywords 최대 키워드 수
     * @return 키워드 점수 목록 (점수 내림차순)
     */
    fun extractKeywords(
        text: String,
        title: String? = null,
        maxKeywords: Int = maxKeywordsPerDoc
    ): List<KeywordScore> {
        if (text.isBlank()) return emptyList()

        val startTime = System.currentTimeMillis()

        try {
            // 1️⃣ 본문에서 명사 추출
            val bodyNouns = morphemeAnalyzerService.extractNouns(text)
            
            // 2️⃣ 제목에서 명사 추출 (가중치 적용)
            val titleNouns = title?.let { 
                morphemeAnalyzerService.extractNouns(it) 
            } ?: emptyList()

            // 3️⃣ 필터링 (길이, 불용어)
            val filteredBodyNouns = filterKeywords(bodyNouns)
            val filteredTitleNouns = filterKeywords(titleNouns)

            // 4️⃣ 빈도수 계산
            val bodyFrequency = filteredBodyNouns.groupingBy { it }.eachCount()
            val titleFrequency = filteredTitleNouns.groupingBy { it }.eachCount()

            // 5️⃣ 점수 계산 (TF + 제목 가중치)
            val allKeywords = (bodyFrequency.keys + titleFrequency.keys).distinct()
            val documentLength = filteredBodyNouns.size.coerceAtLeast(1)

            val scores = allKeywords.map { keyword ->
                val bodyCount = bodyFrequency[keyword] ?: 0
                val titleCount = titleFrequency[keyword] ?: 0
                
                // TF 계산 (정규화)
                val tf = bodyCount.toFloat() / sqrt(documentLength.toFloat())
                
                // 제목 가중치 (제목에 있으면 2배)
                val titleBoost = if (titleCount > 0) 2.0f else 1.0f
                
                // 최종 점수
                val score = tf * titleBoost
                
                KeywordScore(
                    word = keyword,
                    count = bodyCount + titleCount,
                    score = score
                )
            }
            .sortedByDescending { it.score }
            .take(maxKeywords)

            val duration = System.currentTimeMillis() - startTime
            logger.debug { 
                "Keywords extracted: ${scores.size} from ${bodyNouns.size} nouns, duration=${duration}ms" 
            }

            return scores

        } catch (e: Exception) {
            logger.error(e) { "Failed to extract keywords from text (length=${text.length})" }
            return emptyList()
        }
    }

    /**
     * 명사만 추출 (필터링 포함)
     * 
     * @param text 분석할 텍스트
     * @return 필터링된 명사 목록
     */
    fun extractNouns(text: String): List<String> {
        val nouns = morphemeAnalyzerService.extractNouns(text)
        return filterKeywords(nouns)
    }

    /**
     * 키워드 필터링
     * 
     * - 길이 제한
     * - 불용어 제거
     * - 숫자만 있는 토큰 제거
     */
    private fun filterKeywords(keywords: List<String>): List<String> {
        return keywords.filter { keyword ->
            keyword.length in minKeywordLength..maxKeywordLength &&
            keyword !in stopwords &&
            !keyword.all { it.isDigit() } &&
            !keyword.matches(Regex("^[\\p{Punct}]+$"))
        }
    }

    /**
     * 배치 키워드 추출
     * 
     * 여러 문서에서 키워드를 추출하고 전체 빈도수 집계
     * 
     * @param documents 문서 목록 (text, title 쌍)
     * @return 전체 키워드 빈도 맵
     */
    fun extractKeywordsBatch(documents: List<Pair<String, String?>>): Map<String, Int> {
        val allKeywords = mutableMapOf<String, Int>()

        documents.forEach { (text, title) ->
            val keywords = extractKeywords(text, title)
            keywords.forEach { kw ->
                allKeywords.merge(kw.word, kw.count) { old, new -> old + new }
            }
        }

        return allKeywords.toList()
            .sortedByDescending { it.second }
            .toMap()
    }

    /**
     * TF-IDF 점수 계산
     * 
     * @param termFrequency 문서 내 빈도수
     * @param documentFrequency 해당 단어가 등장하는 문서 수
     * @param totalDocuments 전체 문서 수
     * @return TF-IDF 점수
     */
    fun calculateTfIdf(
        termFrequency: Int,
        documentFrequency: Int,
        totalDocuments: Int
    ): Float {
        if (termFrequency == 0 || documentFrequency == 0 || totalDocuments == 0) {
            return 0f
        }

        val tf = 1 + ln(termFrequency.toDouble())
        val idf = ln(totalDocuments.toDouble() / documentFrequency)
        
        return (tf * idf).toFloat()
    }

    /**
     * 키워드 점수 재계산 (IDF 적용)
     * 
     * @param keywords 키워드 목록
     * @param documentFrequencies 각 키워드의 문서 빈도
     * @param totalDocuments 전체 문서 수
     * @return IDF가 적용된 키워드 점수 목록
     */
    fun recalculateWithIdf(
        keywords: List<KeywordScore>,
        documentFrequencies: Map<String, Int>,
        totalDocuments: Int
    ): List<KeywordScore> {
        return keywords.map { kw ->
            val df = documentFrequencies[kw.word] ?: 1
            val tfidf = calculateTfIdf(kw.count, df, totalDocuments)
            kw.copy(score = tfidf)
        }.sortedByDescending { it.score }
    }
}
