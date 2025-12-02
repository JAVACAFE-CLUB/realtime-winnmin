#!/bin/bash

echo "🚀 RTW 인프라 실행 (Elasticsearch + Kibana + Kafka + MongoDB + Redis)"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 서비스 상태 확인 함수
check_service_health() {
    local service_name=$1
    local url=$2
    local max_attempts=20
    local attempt=1

    echo "🔍 $service_name 상태 확인 중..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            echo "✅ $service_name 준비 완료!"
            return 0
        fi
        echo "⏳ $service_name 시작 대기 중... ($attempt/$max_attempts)"
        sleep 5
        ((attempt++))
    done

    echo "❌ $service_name 시작 실패 또는 시간 초과"
    return 1
}

# MongoDB 상태 확인 함수
check_mongodb_health() {
    local max_attempts=20
    local attempt=1

    echo "🔍 MongoDB 상태 확인 중..."
    while [ $attempt -le $max_attempts ]; do
        if docker exec mongodb mongosh --eval "db.adminCommand('ping')" --quiet > /dev/null 2>&1; then
            echo "✅ MongoDB 준비 완료!"
            return 0
        fi
        echo "⏳ MongoDB 시작 대기 중... ($attempt/$max_attempts)"
        sleep 5
        ((attempt++))
    done

    echo "❌ MongoDB 시작 실패 또는 시간 초과"
    return 1
}

# Redis 상태 확인 함수
check_redis_health() {
    local max_attempts=10
    local attempt=1

    echo "🔍 Redis 상태 확인 중..."
    while [ $attempt -le $max_attempts ]; do
        if docker exec redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
            echo "✅ Redis 준비 완료!"
            return 0
        fi
        echo "⏳ Redis 시작 대기 중... ($attempt/$max_attempts)"
        sleep 3
        ((attempt++))
    done

    echo "❌ Redis 시작 실패 또는 시간 초과"
    return 1
}

# Kafka 토픽 생성 함수
create_kafka_topics() {
    echo "📝 기본 Kafka 토픽 생성 중..."

    # 로그 관련 토픽들 생성
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic logs --partitions 3 --replication-factor 1 --if-not-exists
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic events --partitions 3 --replication-factor 1 --if-not-exists
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic metrics --partitions 3 --replication-factor 1 --if-not-exists

    # 크롤러 관련 토픽들 생성
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic twitter-items --partitions 10 --replication-factor 1 --if-not-exists
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic rss-items --partitions 10 --replication-factor 1 --if-not-exists
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic wiki-items --partitions 10 --replication-factor 1 --if-not-exists

    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic refined-twitter --partitions 10 --replication-factor 1 --if-not-exists
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic refined-rss --partitions 10 --replication-factor 1 --if-not-exists
    docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic refined-wiki --partitions 10 --replication-factor 1 --if-not-exists

    echo "✅ 기본 토픽 생성 완료"
}

# MongoDB 초기 설정 함수
setup_mongodb() {
    echo "📝 MongoDB 초기 설정 중..."

    # 초기화 스크립트가 실행되었는지 확인
    if docker exec mongodb mongosh --eval "db.getSiblingDB('realtime_winnmin').getCollectionNames()" --quiet | grep -q "origin_data"; then
        echo "✅ MongoDB가 이미 초기화되어 있습니다."
    else
        echo "🔧 MongoDB 컬렉션 및 인덱스 생성 중..."

        # 인덱스 생성 스크립트 실행
        docker exec mongodb mongosh --eval "
        db = db.getSiblingDB('realtime_winnmin');

        // origin_data 컬렉션 생성
        if (!db.getCollectionNames().includes('origin_data')) {
            db.createCollection('origin_data');
            print('✅ origin_data 컬렉션 생성 완료');
        }

        // 인덱스 생성
        db.origin_data.createIndex({ 'createdAt': -1 });
        db.origin_data.createIndex({ 'prefix': 1, 'createdAt': -1 });
        db.origin_data.createIndex({ 'source': 1 });
        db.origin_data.createIndex({ 'createdAt': 1 }, { expireAfterSeconds: 2592000 });

        print('✅ 인덱스 생성 완료');
        " --quiet
    fi

    echo "✅ MongoDB 설정 완료"
}

# Elasticsearch 인덱스 초기화 함수
setup_elasticsearch_indices() {
    echo "📝 Elasticsearch 인덱스 설정 중..."

    # rtw-articles 인덱스 존재 여부 확인
    ARTICLE_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:9200/rtw-articles")
    if [ "$ARTICLE_EXISTS" = "200" ]; then
        echo "   ✅ rtw-articles 인덱스가 이미 존재합니다."
    else
        echo "   📦 rtw-articles 인덱스 생성 중..."
        RESULT=$(curl -s -X PUT "http://localhost:9200/rtw-articles" \
            -H "Content-Type: application/json" \
            -d @"${SCRIPT_DIR}/elasticsearch/init-scripts/article-index-settings.json")
        
        if echo "$RESULT" | grep -q '"acknowledged":true'; then
            echo "   ✅ rtw-articles 인덱스 생성 완료!"
        else
            echo "   ⚠️ rtw-articles 인덱스 생성 실패: $RESULT"
        fi
    fi

    # rtw-keywords 인덱스 존재 여부 확인
    KEYWORD_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:9200/rtw-keywords")
    if [ "$KEYWORD_EXISTS" = "200" ]; then
        echo "   ✅ rtw-keywords 인덱스가 이미 존재합니다."
    else
        echo "   📦 rtw-keywords 인덱스 생성 중..."
        RESULT2=$(curl -s -X PUT "http://localhost:9200/rtw-keywords" \
            -H "Content-Type: application/json" \
            -d @"${SCRIPT_DIR}/elasticsearch/init-scripts/keyword-index-settings.json")
        
        if echo "$RESULT2" | grep -q '"acknowledged":true'; then
            echo "   ✅ rtw-keywords 인덱스 생성 완료!"
        else
            echo "   ⚠️ rtw-keywords 인덱스 생성 실패: $RESULT2"
        fi
    fi

    echo "✅ Elasticsearch 인덱스 설정 완료"
}

# 1. 기존 컨테이너 중지 (볼륨은 보존)
echo "🛑 기존 컨테이너 중지 중..."
docker-compose down

# 2. Elasticsearch 커스텀 이미지 빌드 (Nori 플러그인 포함)
echo ""
echo "🔨 Elasticsearch 커스텀 이미지 빌드 중 (Nori 플러그인 포함)..."
docker-compose build elasticsearch

# 3. 모든 서비스 실행
echo ""
echo "🏃 모든 서비스 실행 중..."
docker-compose up -d

# 4. 서비스별 순차적 상태 확인
echo ""
echo "⏰ 서비스 시작 대기 중..."

# Kafka 확인 (KRaft 모드)
echo "🔍 Kafka 상태 확인 중..."
kafka_attempt=1
kafka_max_attempts=20
while [ $kafka_attempt -le $kafka_max_attempts ]; do
    if docker exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; then
        echo "✅ Kafka 준비 완료!"
        break
    fi
    echo "⏳ Kafka 시작 대기 중... ($kafka_attempt/$kafka_max_attempts)"
    sleep 5
    ((kafka_attempt++))
done

if [ $kafka_attempt -gt $kafka_max_attempts ]; then
    echo "❌ Kafka 시작 실패"
    echo "🔍 Kafka 컨테이너 로그 확인:"
    docker logs kafka --tail 10
    exit 1
fi

# Elasticsearch 확인
if ! check_service_health "Elasticsearch" "http://localhost:9200/_cluster/health"; then
    echo "❌ Elasticsearch 시작 실패"
    echo "🔍 Elasticsearch 컨테이너 로그 확인:"
    docker logs elasticsearch --tail 10
    exit 1
fi

# Kibana 확인
if ! check_service_health "Kibana" "http://localhost:5601/api/status"; then
    echo "❌ Kibana 시작 실패"
    echo "🔍 Kibana 컨테이너 로그 확인:"
    docker logs kibana --tail 10
    exit 1
fi

# MongoDB 확인
if ! check_mongodb_health; then
    echo "❌ MongoDB 시작 실패"
    echo "🔍 MongoDB 컨테이너 로그 확인:"
    docker logs mongodb --tail 10
    exit 1
fi

# Redis 확인
if ! check_redis_health; then
    echo "⚠️ Redis 시작 실패 (계속 진행)"
    echo "🔍 Redis 컨테이너 로그 확인:"
    docker logs redis --tail 10
fi

# Prometheus 확인
if ! check_service_health "Prometheus" "http://localhost:9090/-/healthy"; then
    echo "⚠️ Prometheus 시작 실패 (계속 진행)"
fi

# Grafana 확인
if ! check_service_health "Grafana" "http://localhost:3000/api/health"; then
    echo "⚠️ Grafana 시작 실패 (계속 진행)"
fi

# Kafka Exporter 확인
if ! check_service_health "Kafka Exporter" "http://localhost:9308/metrics"; then
    echo "⚠️ Kafka Exporter 시작 실패 (계속 진행)"
fi

# 5. Elasticsearch 상태 상세 확인
echo ""
echo "📊 Elasticsearch 클러스터 상태:"
curl -s http://localhost:9200/_cluster/health?pretty

# 6. Nori 플러그인 확인
echo ""
echo "🔍 Nori 플러그인 확인..."
PLUGINS=$(curl -s "http://localhost:9200/_cat/plugins?format=json")
if echo "$PLUGINS" | grep -q "analysis-nori"; then
    echo "✅ analysis-nori 플러그인이 설치되어 있습니다."
else
    echo "❌ analysis-nori 플러그인이 설치되어 있지 않습니다!"
    echo "   Dockerfile을 확인하고 'docker-compose build elasticsearch'를 실행하세요."
fi

# 7. Kafka 토픽 생성
echo ""
create_kafka_topics

# 8. MongoDB 초기 설정
echo ""
setup_mongodb

# 9. Elasticsearch 인덱스 초기화
echo ""
setup_elasticsearch_indices

# 10. 최종 상태 확인
echo ""
echo "============================================"
echo "📊 최종 상태 확인"
echo "============================================"

# Kafka 토픽 목록 확인
echo ""
echo "📋 Kafka 토픽 목록:"
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# MongoDB 컬렉션 확인
echo ""
echo "📋 MongoDB 컬렉션 목록:"
docker exec mongodb mongosh --eval "db.getSiblingDB('realtime_winnmin').getCollectionNames()" --quiet

# Elasticsearch 인덱스 확인
echo ""
echo "📋 Elasticsearch 인덱스:"
curl -s "http://localhost:9200/_cat/indices?v&index=rtw-*"

# Redis 확인
echo ""
echo "📋 Redis 상태:"
docker exec redis redis-cli INFO server | grep -E "(redis_version|uptime_in_seconds)"

# 실행 중인 컨테이너 확인
echo ""
echo "🐳 실행 중인 컨테이너:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "🎉 모든 서비스 설정 완료!"
echo ""
echo "============================================"
echo "📊 서비스 접속 정보"
echo "============================================"
echo "  • Elasticsearch: http://localhost:9200"
echo "  • Kibana: http://localhost:5601"
echo "  • Kafka: localhost:9092"
echo "  • Kafka UI: http://localhost:9080"
echo "  • MongoDB: mongodb://localhost:27017"
echo "  • Mongo Express: http://localhost:9081 (admin/admin123)"
echo "  • Redis: localhost:6379"
echo "  • Redis Insight: http://localhost:5540"
echo "  • Grafana: http://localhost:3000 (admin/admin)"
echo "  • Prometheus: http://localhost:9090"
echo ""
echo "🔐 MongoDB 접속 정보:"
echo "  • 관리자: admin / admin123"
echo "  • 앱 사용자: rtw_app / rtw_password123"
echo "  • 데이터베이스: realtime_winnmin"
echo ""
echo "🔧 Elasticsearch 인덱스:"
echo "  • rtw-articles: 문서 색인용 (Nori 형태소 분석기)"
echo "  • rtw-keywords: 키워드 통계용"
echo ""
echo "💡 팁:"
echo "  • 데이터를 완전히 초기화하려면 './reset.sh'를 실행하세요."
echo "  • ES 인덱스만 리셋하려면 './elasticsearch/init-scripts/reset-indices.sh'를 실행하세요."
echo "  • 형태소 분석 테스트: './elasticsearch/init-scripts/init-indices.sh' 실행 후 확인"
