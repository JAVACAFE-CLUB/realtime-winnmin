# Realtime Winnmin (실시간 검색어 서비스)

실시간 뉴스/정보를 수집하고, 형태소 분석을 통해 **오늘의 키워드**를 추출하는 검색 서비스입니다.

## 🏗️ 아키텍처

```
┌──────────────┐    ┌──────────────┐    ┌─────────────┐    ┌─────────────┐
│ rtw-collector│───▶│ rtw-dataclean│───▶│  rtw-index  │───▶│  rtw-serve  │
│   (수집)      │    │    (정제)     │    │   (색인)     │    │   (조회)     │
└──────────────┘    └──────────────┘    └─────────────┘    └─────────────┘
      │                   │                  │                   │
      ▼                   ▼                  ▼                   ▼
   Kafka             MongoDB          Elasticsearch          REST API
 (raw-*)          (full_text)        + Redis Cache
```

## 🔄 데이터 처리 흐름 예시

"삼성전자, AI 반도체 신제품 발표" 라는 뉴스가 수집되었을 때:

### 1단계: 수집 (rtw-collector)

```json
// Kafka 토픽: raw-rss
{
  "url": "https://news.example.com/12345",
  "title": "삼성전자, AI 반도체 신제품 발표",
  "content": "<p>삼성전자가 <b>인공지능</b> 반도체...</p>",
  "source": "연합뉴스",
  "collectedAt": "2024-01-15T10:30:00"
}
```

### 2단계: 정제 (rtw-dataclean)

```json
// MongoDB: full_text_data 컬렉션
{
  "refinedId": "rss-abc123",
  "title": "삼성전자, AI 반도체 신제품 발표",
  "fullText": "삼성전자가 인공지능 반도체 신제품을 발표했다...",  // HTML 태그 제거
  "source": "연합뉴스",
  "sourceType": "RSS"
}

// Kafka 토픽: refined-rss → { "refinedId": "rss-abc123" }
```

### 3단계: 색인 (rtw-index)

```json
// Elasticsearch: rtw-articles 인덱스
{
  "refinedId": "rss-abc123",
  "title": "삼성전자, AI 반도체 신제품 발표",
  "fullText": "삼성전자가 인공지능 반도체 신제품을 발표했다...",
  "keywords": ["삼성전자", "인공지능", "반도체", "신제품"],  // Nori 형태소 분석 결과
  "keywordScores": [
    { "word": "삼성전자", "count": 5, "score": 12.5 },
    { "word": "인공지능", "count": 3, "score": 8.2 }
  ],
  "indexedAt": "2024-01-15T10:30:05"
}
```

### 4단계: 집계 (rtw-index 스케줄러, 10분 주기)

```json
// Elasticsearch: rtw-keywords 인덱스
{
  "date": "2024-01-15",
  "keyword": "삼성전자",
  "rank": 1,
  "frequency": 1523,
  "sourceBreakdown": { "rss": 800, "wiki": 500, "twitter": 223 }
}

// Redis: rtw:keyword:daily:2024-01-15 (String/JSON)
{
  "date": "2024-01-15",
  "keywords": [
    { "keyword": "삼성전자", "rank": 1, "frequency": 1523 },
    { "keyword": "인공지능", "rank": 2, "frequency": 1102 }
  ],
  "generatedAt": "2024-01-15T10:40:00"
}
```

### 5단계: 조회 (rtw-serve)

```bash
GET /api/v1/keywords/today
```
```json
{
  "date": "2024-01-15",
  "keywords": [
    { "keyword": "삼성전자", "rank": 1, "frequency": 1523, "rankChange": 2 },
    { "keyword": "인공지능", "rank": 2, "frequency": 1102, "isNew": true }
  ],
  "cached": true  // Redis에서 조회
}
```

## 🛠️ 기술 스택

| 영역 | 기술 |
|------|------|
| Language | Kotlin 1.9, Java 17 |
| Framework | Spring Boot 3.2, Spring WebFlux |
| Message Queue | Apache Kafka (KRaft 모드) |
| Database | MongoDB 7.0 |
| Search Engine | Elasticsearch 8.11 + Nori 형태소 분석기 |
| Cache | Redis 7 |
| Monitoring | Prometheus, Grafana |

## 📦 모듈 구성

### rtw-collector (수집)
- RSS, Wikipedia, Twitter 등 다양한 소스에서 실시간 데이터 수집
- WebClient 기반 비동기 HTTP 요청
- 수집 데이터를 Kafka `raw-*` 토픽으로 발행

### rtw-dataclean (정제)
- Kafka Consumer로 원시 데이터 수신
- HTML 태그 제거, 텍스트 정규화, 중복 제거
- 정제된 데이터를 MongoDB `full_text_data` 컬렉션에 저장
- Kafka `refined-*` 토픽으로 발행

### rtw-index (색인)
- Kafka Consumer 배치 처리 (최대 500건/poll)
- MongoDB에서 풀텍스트 조회 후 ES Bulk 색인
- **Nori 형태소 분석기**로 명사 기반 키워드 추출
  - 사용자 사전: IT/금융 도메인 복합어 등록
  - 품사 필터: 명사만 추출 (조사, 어미, 동사 제거)
  - 불용어 필터: 의미 없는 단어 제거
- **ES Terms Aggregation**으로 일별/시간별 키워드 집계
  - rank 는 doc_count(빈도) 내림차순 반환
- 스케줄러가 집계 결과를 Redis에 캐싱 (10분/1시간 주기)
- **키워드 Score 계산**: `ln(frequency + 1) × (1 / √rank) × 100`
  - 로그 스케일로 빈도 차이 완화, 상위 순위에 높은 가중치 부여
  - 하지만 rank 와는 별개임

### rtw-serve (조회)
- **검색 API**: Multi-Match 쿼리, 하이라이트, 자동완성
- **키워드 API**: 오늘의 키워드, 시간별 키워드, 트렌드 조회
- Cache-Aside 패턴: Redis 캐시 우선 조회, 미스 시 ES fallback

### rtw-core (공유)
- 모듈 간 공유 모델 (ES Document, MongoDB Document, DTO)

### rtw-tool (인프라)
- Docker Compose 기반 인프라 구성
- Kafka, Elasticsearch, MongoDB, Redis, Prometheus, Grafana

## 📨 Kafka 사용

| 토픽 | 발행 | 소비 | 용도                       |
|------|------|------|--------------------------|
| `raw-rss`, `raw-wiki`, `raw-twitter` | collector | dataclean | 원시 데이터 전달(rawId만 전달)      |
| `refined-rss`, `refined-wiki`, `refined-twitter` | dataclean | index | 정제 완료 알림 (refinedId만 전달) |

- **토픽 분리**: 소스별 토픽 분리로 독립적 처리량 조절 및 장애 격리
- **배치 Consumer**: `max-poll-records=500`으로 한 번에 최대 500건 처리
- **수동 오프셋 커밋**: 색인 성공 후에만 커밋, 실패 시 재처리 보장
- **Consumer Group 분리**: 토픽별 독립적인 Consumer Group으로 병렬 처리

## 🔑 캐싱 전략

| 키 패턴 | TTL | 갱신 주기 |
|---------|-----|----------|
| `rtw:keyword:daily:{date}` | 24시간 | 10분 |
| `rtw:keyword:hourly:{date}:{hour}` | 2시간 | 매 시간 |

- **Write-Through**: rtw-index 스케줄러가 ES 집계 → Redis 저장
- **Cache-Aside**: rtw-serve가 Redis 조회 → 미스 시 ES fallback

## 🔍 Elasticsearch 사용

| 인덱스 | 용도 | 샤드 | 리플리카 |
|--------|------|------|----------|
| `rtw-articles` | 문서 색인 (전문검색) | 3 | 1 |
| `rtw-keywords` | 키워드 통계 | 1 | 0 |

### 색인 (Write)
- **Bulk API**: 배치 단위로 대량 색인, `refresh=false`로 성능 최적화
- **Nori 분석기**: 색인 시 `korean_standard` (명사 추출), 검색 시 `korean_search` (동의어 확장)
- **사용자 사전**: IT/금융 도메인 복합어 등록 (삼성전자, 인공지능 등)

### 집계 (Aggregation)
- **Terms Aggregation**: 키워드 빈도 집계
- **Sub-Aggregation**: 소스별 분포(`by_source`), 시간별 추이(`by_hour`)

### 검색 (Read)
- **Multi-Match**: title^3, fullText, keywords^2 가중치 적용
- **Highlight**: `<em>` 태그로 검색어 강조
- **Match Phrase Prefix**: 자동완성 지원

## 📁 프로젝트 구조

```
realtime-winnmin/
├── rtw-core/          # 공유 모델
├── rtw-collector/     # 데이터 수집
├── rtw-dataclean/     # 데이터 정제
├── rtw-index/         # 색인 + 집계 + 캐싱
├── rtw-serve/         # 검색 + 조회 API
├── rtw-tool/          # Docker 인프라
├── build.gradle.kts
└── settings.gradle.kts
```
