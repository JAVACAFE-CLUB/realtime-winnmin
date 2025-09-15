#!/bin/bash

echo "🛑 Elasticsearch + Kibana + Kafka 서비스 중지"

# 실행 중인 컨테이너 확인
echo "🔍 현재 실행 중인 관련 컨테이너:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(elasticsearch|kibana|kafka|zookeeper|kafka-ui)"

echo ""

# 1. Docker Compose로 모든 서비스 중지
echo "🏃 모든 서비스 중지 중..."
docker-compose down

# 2. 개별 컨테이너가 남아있는지 확인하고 강제 중지
echo ""
echo "🔍 남아있는 컨테이너 확인 및 정리..."

# 관련 컨테이너들을 찾아서 중지
containers=$(docker ps -q --filter "name=elasticsearch" --filter "name=kibana" --filter "name=kafka" --filter "name=zookeeper" --filter "name=kafka-ui")

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
echo "  📊 Elasticsearch 데이터: $(docker volume inspect elasticsearch_data --format '{{.Mountpoint}}' 2>/dev/null || echo '볼륨 없음')"
echo "  📈 Kibana 데이터: $(docker volume inspect kibana_data --format '{{.Mountpoint}}' 2>/dev/null || echo '볼륨 없음')"
echo "  📝 Kafka 데이터: $(docker volume inspect kafka_data --format '{{.Mountpoint}}' 2>/dev/null || echo '볼륨 없음')"

# 5. 포트 사용 확인
echo ""
echo "🔌 포트 사용 상태 확인:"
ports=("9200" "9300" "5601" "9092" "9093" "8080" "9090")
for port in "${ports[@]}"; do
    if lsof -i :$port >/dev/null 2>&1; then
        echo "  ⚠️  포트 $port: 사용 중 (다른 프로세스가 사용 중일 수 있음)"
    else
        echo "  ✅ 포트 $port: 사용 가능"
    fi
done

echo ""
echo "✅ 모든 서비스가 중지되었습니다!"
echo ""
echo "ℹ️  참고사항:"
echo "  • 데이터 볼륨은 보존되었습니다 (다음 실행 시 데이터 유지)"
echo "  • 완전한 데이터 초기화가 필요한 경우 './reset.sh'를 실행하세요"
echo "  • 서비스 재시작: './start.sh'"
echo ""
echo "💡 유용한 명령어들:"
echo "  • 볼륨 목록 확인: docker volume ls"
echo "  • 사용하지 않는 볼륨 정리: docker volume prune"
echo "  • 모든 Docker 리소스 정리: docker system prune -a"
