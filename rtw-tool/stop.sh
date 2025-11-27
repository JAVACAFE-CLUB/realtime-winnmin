#!/bin/bash

echo "🛑 Elasticsearch + Kibana + Kafka + MongoDB 서비스 중지"

# 실행 중인 컨테이너 확인
echo "🔍 현재 실행 중인 관련 컨테이너:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(elasticsearch|kibana|kafka|mongodb|mongo-express|kafka-ui|prometheus|grafana|kafka-exporter)"

echo ""

# 1. Docker Compose로 모든 서비스 중지
echo "🏃 모든 서비스 중지 중..."
docker-compose down

# 2. 개별 컨테이너가 남아있는지 확인하고 강제 중지
echo ""
echo "🔍 남아있는 컨테이너 확인 및 정리..."

# 관련 컨테이너들을 찾아서 중지
containers=$(docker ps -q \
    --filter "name=elasticsearch" \
    --filter "name=kibana" \
    --filter "name=kafka" \
    --filter "name=kafka-ui" \
    --filter "name=kafka-exporter" \
    --filter "name=prometheus" \
    --filter "name=grafana" \
    --filter "name=mongodb" \
    --filter "name=mongo-express")

if [ ! -z "$containers" ]; then
    echo "⚠️  남아있는 컨테이너 발견, 강제 중지 중..."
    docker stop $containers
    docker rm $containers
else
    echo "✅ 모든 관련 컨테이너가 정상적으로 중지되었습니다."
fi

# 3. 네트워크 상태 확인
echo ""
echo "🌐 Docker 네트워크 상태 확인..."
if docker network ls | grep -q "elastic"; then
    echo "ℹ️  'elastic' 네트워크가 여전히 존재합니다 (정상)"
else
    echo "✅ 네트워크가 정리되었습니다"
fi

# 4. 볼륨 상태 확인 (삭제하지 않고 정보만 표시)
echo ""
echo "💾 데이터 볼륨 상태:"

# Elasticsearch 볼륨
if docker volume inspect rtw-tool_elasticsearch_data >/dev/null 2>&1; then
    es_size=$(docker run --rm -v rtw-tool_elasticsearch_data:/data alpine du -sh /data 2>/dev/null | cut -f1 || echo "N/A")
    echo "  📊 Elasticsearch 데이터: 존재함 (크기: $es_size)"
else
    echo "  📊 Elasticsearch 데이터: 볼륨 없음"
fi

# Kibana 볼륨
if docker volume inspect rtw-tool_kibana_data >/dev/null 2>&1; then
    kibana_size=$(docker run --rm -v rtw-tool_kibana_data:/data alpine du -sh /data 2>/dev/null | cut -f1 || echo "N/A")
    echo "  📈 Kibana 데이터: 존재함 (크기: $kibana_size)"
else
    echo "  📈 Kibana 데이터: 볼륨 없음"
fi

# MongoDB 볼륨
if docker volume inspect rtw-tool_mongodb_data >/dev/null 2>&1; then
    mongo_size=$(docker run --rm -v rtw-tool_mongodb_data:/data alpine du -sh /data 2>/dev/null | cut -f1 || echo "N/A")
    echo "  🗄️  MongoDB 데이터: 존재함 (크기: $mongo_size)"
else
    echo "  🗄️  MongoDB 데이터: 볼륨 없음"
fi

# MongoDB Config 볼륨
if docker volume inspect rtw-tool_mongodb_config >/dev/null 2>&1; then
    echo "  ⚙️  MongoDB 설정: 존재함"
else
    echo "  ⚙️  MongoDB 설정: 볼륨 없음"
fi

# Prometheus 볼륨
if docker volume inspect rtw-tool_prometheus_data >/dev/null 2>&1; then
    prometheus_size=$(docker run --rm -v rtw-tool_prometheus_data:/data alpine du -sh /data 2>/dev/null | cut -f1 || echo "N/A")
    echo "  📈 Prometheus 데이터: 존재함 (크기: $prometheus_size)"
else
    echo "  📈 Prometheus 데이터: 볼륨 없음"
fi

# Grafana 볼륨
if docker volume inspect rtw-tool_grafana_data >/dev/null 2>&1; then
    grafana_size=$(docker run --rm -v rtw-tool_grafana_data:/data alpine du -sh /data 2>/dev/null | cut -f1 || echo "N/A")
    echo "  📊 Grafana 데이터: 존재함 (크기: $grafana_size)"
else
    echo "  📊 Grafana 데이터: 볼륨 없음"
fi

# Kafka 로컬 디렉토리
if [ -d "/Users/usmin/kafka-data" ]; then
    kafka_size=$(du -sh /Users/usmin/kafka-data 2>/dev/null | cut -f1 || echo "N/A")
    kafka_count=$(find /Users/usmin/kafka-data -type f 2>/dev/null | wc -l | tr -d ' ')
    echo "  📝 Kafka 데이터: 존재함 (크기: $kafka_size, 파일: ${kafka_count}개)"
else
    echo "  📝 Kafka 데이터: 디렉토리 없음"
fi

# 5. 포트 사용 확인
echo ""
echo "🔌 포트 사용 상태 확인:"
ports=("9200" "9300" "5601" "9092" "9093" "9080" "27017" "9081" "9090" "3000" "9308")
port_names=("Elasticsearch HTTP" "Elasticsearch Transport" "Kibana" "Kafka" "Kafka Controller" "Kafka UI" "MongoDB" "Mongo Express" "Prometheus" "Grafana" "Kafka Exporter")

for i in "${!ports[@]}"; do
    port="${ports[$i]}"
    name="${port_names[$i]}"
    if lsof -i :$port >/dev/null 2>&1; then
        echo "  ⚠️  포트 $port ($name): 사용 중 (다른 프로세스가 사용 중일 수 있음)"
    else
        echo "  ✅ 포트 $port ($name): 사용 가능"
    fi
done

# 6. 데이터 통계 요약
echo ""
echo "📊 데이터 보존 현황 요약:"
total_volumes=$(docker volume ls -q | grep -c "rtw-tool" || echo "0")
echo "  • Docker 볼륨: ${total_volumes}개"
if [ -d "/Users/usmin/kafka-data" ]; then
    echo "  • Kafka 로컬 데이터: 보존됨"
fi

echo ""
echo "✅ 모든 서비스가 중지되었습니다!"
echo ""
echo "ℹ️  참고사항:"
echo "  • 데이터 볼륨은 보존되었습니다 (다음 실행 시 데이터 유지)"
echo "  • Kafka 데이터는 /Users/usmin/kafka-data에 저장되어 있습니다"
echo "  • MongoDB 데이터는 Docker 볼륨에 저장되어 있습니다"
echo "  • 완전한 데이터 초기화가 필요한 경우 './reset.sh'를 실행하세요"
echo "  • 서비스 재시작: './start.sh'"
echo ""
echo "💡 유용한 명령어들:"
echo ""
echo "  [볼륨 관리]"
echo "  • 볼륨 목록 확인: docker volume ls | grep rtw-tool"
echo "  • 볼륨 상세 정보: docker volume inspect rtw-tool_mongodb_data"
echo "  • 사용하지 않는 볼륨 정리: docker volume prune"
echo ""
echo "  [데이터 확인]"
echo "  • Kafka 데이터 크기: du -sh /Users/usmin/kafka-data"
echo "  • MongoDB 볼륨 크기: docker run --rm -v rtw-tool_mongodb_data:/data alpine du -sh /data"
echo ""
echo "  [시스템 정리]"
echo "  • 중지된 컨테이너 정리: docker container prune"
echo "  • 모든 Docker 리소스 정리: docker system prune -a"
