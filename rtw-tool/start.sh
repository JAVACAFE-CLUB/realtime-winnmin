#!/bin/bash

echo "🚀 Elasticsearch + Kibana + Kafka + MongoDB + Nori 플러그인 설치 및 실행"

# Nori 플러그인이 설치되어 있는지 확인하는 함수
check_nori_plugin() {
    if docker exec elasticsearch /usr/share/elasticsearch/bin/elasticsearch-plugin list 2>/dev/null | grep -q "analysis-nori"; then
        return 0  # 설치됨
    else
        return 1  # 설치 안됨
    fi
}

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

# 1. 기존 컨테이너 중지 (볼륨은 보존)
echo "🛑 기존 컨테이너 중지 중..."
docker-compose down

# 2. 모든 서비스 실행
echo "🏃 모든 서비스 실행 중..."
docker-compose up -d

# 3. 서비스별 순차적 상태 확인
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

# Prometheus 확인
if ! check_service_health "Prometheus" "http://localhost:9090/-/healthy"; then
    echo "⚠️ Prometheus 시작 실패 (계속 진행)"
    echo "🔍 Prometheus 컨테이너 로그 확인:"
    docker logs prometheus --tail 10
fi

# Grafana 확인
if ! check_service_health "Grafana" "http://localhost:3000/api/health"; then
    echo "⚠️ Grafana 시작 실패 (계속 진행)"
    echo "🔍 Grafana 컨테이너 로그 확인:"
    docker logs grafana --tail 10
fi

# Kafka Exporter 확인
if ! check_service_health "Kafka Exporter" "http://localhost:9308/metrics"; then
    echo "⚠️ Kafka Exporter 시작 실패 (계속 진행)"
    echo "🔍 Kafka Exporter 컨테이너 로그 확인:"
    docker logs kafka-exporter --tail 10
fi

# 4. Elasticsearch 상태 상세 확인
echo ""
echo "📊 Elasticsearch 클러스터 상태:"
curl -s http://localhost:9200/_cluster/health?pretty

# 5. Nori 플러그인 설치 여부 확인
echo ""
echo "🔍 Nori 플러그인 설치 상태 확인 중..."
if check_nori_plugin; then
    echo "✅ Nori 플러그인이 이미 설치되어 있습니다."
else
    echo "📦 Nori 플러그인 설치 중..."
    docker exec elasticsearch /usr/share/elasticsearch/bin/elasticsearch-plugin install analysis-nori --batch

    echo "🔄 Elasticsearch 재시작 중..."
    docker restart elasticsearch

    echo "⏳ 재시작 대기 중..."
    if ! check_service_health "Elasticsearch (재시작 후)" "http://localhost:9200/_cluster/health"; then
        echo "❌ Elasticsearch 재시작 실패"
        exit 1
    fi
fi

# 6. Kafka 토픽 생성
echo ""
create_kafka_topics

# 7. MongoDB 초기 설정
echo ""
setup_mongodb

# 8. 최종 상태 확인
echo ""
echo "🔍 최종 상태 확인:"

# 설치된 플러그인 확인
echo "📋 설치된 Elasticsearch 플러그인:"
curl -s http://localhost:9200/_nodes/plugins?pretty | grep -A3 -B1 nori || echo "플러그인 확인 실패"

# Kafka 토픽 목록 확인
echo ""
echo "📋 Kafka 토픽 목록:"
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# MongoDB 컬렉션 확인
echo ""
echo "📋 MongoDB 컬렉션 목록:"
docker exec mongodb mongosh --eval "db.getSiblingDB('realtime_winnmin').getCollectionNames()" --quiet

# MongoDB 인덱스 확인
echo ""
echo "📋 MongoDB origin_data 인덱스:"
docker exec mongodb mongosh --eval "db.getSiblingDB('realtime_winnmin').origin_data.getIndexes()" --quiet

# 실행 중인 컨테이너 확인
echo ""
echo "🐳 실행 중인 컨테이너:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "🎉 모든 서비스 설정 완료!"
echo ""
echo "📊 서비스 접속 정보:"
echo "  • Elasticsearch: http://localhost:9200"
echo "  • Kibana: http://localhost:5601"
echo "  • Kafka: localhost:9092"
echo "  • Kafka UI: http://localhost:9080"
echo "  • MongoDB: mongodb://localhost:27017"
echo "  • Mongo Express: http://localhost:9081 (admin/admin123)"
echo "  • Grafana: http://localhost:3000 (admin/admin)"
echo "  • Prometheus: http://localhost:9090"
echo "  • Kafka Exporter: http://localhost:9308/metrics"
echo ""
echo "🔐 MongoDB 접속 정보:"
echo "  • 관리자: admin / admin123"
echo "  • 앱 사용자: rtw_app / rtw_password123"
echo "  • 데이터베이스: realtime_winnmin"
echo ""
echo "📊 모니터링 정보:"
echo "  • Kafka Lag 대시보드: http://localhost:3000/d/kafka-consumer-lag"
echo "  • 상세 가이드: rtw-tool/MONITORING_GUIDE.md 참고"
echo ""
echo "🔧 유용한 명령어들:"
echo ""
echo "  [Kafka]"
echo "  • 토픽 목록: docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list"
echo "  • 메시지 전송: docker exec kafka kafka-console-producer --bootstrap-server localhost:9092 --topic logs"
echo "  • 메시지 수신: docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic logs --from-beginning"
echo ""
echo "  [MongoDB]"
echo "  • MongoDB Shell 접속: docker exec -it mongodb mongosh -u admin -p admin123"
echo "  • 앱 DB 접속: docker exec -it mongodb mongosh -u rtw_app -p rtw_password123 realtime_winnmin"
echo "  • 데이터 조회: docker exec mongodb mongosh --eval 'db.getSiblingDB(\"realtime_winnmin\").origin_data.find().limit(5)' --quiet"
echo "  • 데이터 개수: docker exec mongodb mongosh --eval 'db.getSiblingDB(\"realtime_winnmin\").origin_data.countDocuments()' --quiet"
echo ""
echo "  [Elasticsearch]"
echo "  • 인덱스 목록: curl http://localhost:9200/_cat/indices?v"
echo "  • 클러스터 상태: curl http://localhost:9200/_cluster/health?pretty"
echo ""
echo "💡 팁: 데이터를 완전히 초기화하려면 './reset.sh'를 실행하세요."
