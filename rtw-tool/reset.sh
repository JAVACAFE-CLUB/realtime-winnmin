#!/bin/bash

echo "🗑️  전체 인프라 데이터 완전 초기화"
echo "⚠️  주의: 모든 저장된 데이터가 삭제됩니다!"
echo ""
echo "삭제될 데이터:"
echo "  📊 Elasticsearch 인덱스 및 설정"
echo "  📈 Kibana 대시보드 및 설정"
echo "  📝 Kafka 토픽 및 메시지"
echo "  🗄️  MongoDB 컬렉션 및 문서"
echo "  🔴 Redis 키워드 캐시 데이터"
echo "  📉 Prometheus 메트릭 데이터"
echo "  📊 Grafana 대시보드 설정"
echo "  💾 모든 Docker 볼륨"
echo "  📁 로컬 Kafka 데이터 (/Users/usmin/kafka-data)"
echo ""

read -p "정말로 모든 데이터를 삭제하시겠습니까? (y/N): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "🛑 모든 서비스 중지 및 데이터 삭제 중..."

    # 0. 실행 중인 서비스의 데이터 먼저 삭제 (선택적)
    echo ""
    echo "🔄 실행 중인 서비스 데이터 정리..."
    
    # Redis 데이터 삭제 (컨테이너가 실행 중인 경우)
    if docker ps --format '{{.Names}}' | grep -q '^redis$'; then
        echo "  🔴 Redis 데이터 삭제 중..."
        docker exec redis redis-cli FLUSHALL 2>/dev/null && \
            echo "  ✅ Redis 데이터 삭제 완료" || \
            echo "  ℹ️  Redis 데이터 삭제 건너뜀"
    fi

    # Elasticsearch 인덱스 삭제 (컨테이너가 실행 중인 경우)
    if docker ps --format '{{.Names}}' | grep -q '^elasticsearch$'; then
        echo "  📊 Elasticsearch 인덱스 삭제 중..."
        # rtw- 프리픽스가 붙은 인덱스 삭제
        curl -s -X DELETE "http://localhost:9200/rtw-*" 2>/dev/null && \
            echo "  ✅ Elasticsearch rtw-* 인덱스 삭제 완료" || \
            echo "  ℹ️  Elasticsearch 인덱스 삭제 건너뜀"
    fi

    # 1. 컨테이너 중지 및 볼륨까지 모두 삭제
    echo ""
    echo "🏃 Docker Compose 서비스 및 볼륨 삭제..."
    docker-compose down -v

    # 2. 개별 볼륨이 남아있는지 확인하고 삭제
    echo ""
    echo "🔍 남아있는 관련 볼륨 확인 및 삭제..."

    # rtw-tool 프리픽스가 붙은 볼륨들
    volumes=(
        # Elasticsearch & Kibana
        "rtw-tool_elasticsearch_data"
        "rtw-tool_kibana_data"
        "elasticsearch_data"
        "kibana_data"
        # MongoDB
        "rtw-tool_mongodb_data"
        "rtw-tool_mongodb_config"
        "mongodb_data"
        "mongodb_config"
        # Redis
        "rtw-tool_redis_data"
        "rtw-tool_redis_insight_data"
        "redis_data"
        "redis_insight_data"
        # Monitoring
        "rtw-tool_prometheus_data"
        "rtw-tool_grafana_data"
        "prometheus_data"
        "grafana_data"
        # Kafka
        "kafka_data"
    )

    for volume in "${volumes[@]}"; do
        if docker volume ls -q | grep -q "^${volume}$"; then
            echo "  🗑️  볼륨 삭제: $volume"
            docker volume rm "$volume" 2>/dev/null || echo "    ⚠️  $volume 삭제 실패 (사용 중일 수 있음)"
        fi
    done

    # 추가로 rtw-tool로 시작하는 모든 볼륨 찾아서 삭제
    echo ""
    echo "🔍 rtw-tool 관련 볼륨 전체 검색 및 삭제..."
    rtw_volumes=$(docker volume ls -q | grep "^rtw-tool")
    if [ ! -z "$rtw_volumes" ]; then
        echo "$rtw_volumes" | while read vol; do
            echo "  🗑️  볼륨 삭제: $vol"
            docker volume rm "$vol" 2>/dev/null || echo "    ⚠️  $vol 삭제 실패"
        done
    else
        echo "  ✅ rtw-tool 관련 볼륨 없음"
    fi

    # 3. 로컬 Kafka 데이터 디렉토리 정리
    echo ""
    echo "🗑️  로컬 Kafka 데이터 정리..."

    if [ -d "/Users/usmin/kafka-data" ]; then
        kafka_file_count=$(find /Users/usmin/kafka-data -type f 2>/dev/null | wc -l | tr -d ' ')
        kafka_size=$(du -sh /Users/usmin/kafka-data 2>/dev/null | cut -f1 || echo "N/A")

        if [ "$kafka_file_count" -gt 0 ]; then
            echo "  📝 Kafka 데이터 현황: ${kafka_file_count}개 파일, 크기: ${kafka_size}"
            echo "  🗑️  /Users/usmin/kafka-data 삭제 중..."

            # sudo 권한이 필요할 수 있음
            if sudo rm -rf /Users/usmin/kafka-data/* 2>/dev/null; then
                echo "  ✅ Kafka 데이터 삭제 완료"
            else
                echo "  ⚠️  Kafka 데이터 삭제 실패 (권한 부족 또는 파일 사용 중)"
            fi
        else
            echo "  ℹ️  Kafka 데이터 없음"
        fi
    else
        echo "  ℹ️  /Users/usmin/kafka-data 디렉토리 없음"
    fi

    # 로컬 ./kafka-data 디렉토리도 정리
    if [ -d "./kafka-data" ]; then
        echo "  🗑️  로컬 ./kafka-data 디렉토리 정리 중..."
        rm -rf ./kafka-data/*
        echo "  ✅ 로컬 kafka-data 정리 완료"
    fi

    # 4. 관련 컨테이너가 완전히 제거되었는지 확인
    echo ""
    echo "🧹 남아있는 컨테이너 정리..."
    containers=$(docker ps -aq \
        --filter "name=elasticsearch" \
        --filter "name=kibana" \
        --filter "name=kafka" \
        --filter "name=kafka-ui" \
        --filter "name=kafka-exporter" \
        --filter "name=mongodb" \
        --filter "name=mongo-express" \
        --filter "name=redis" \
        --filter "name=redis-insight" \
        --filter "name=prometheus" \
        --filter "name=grafana")

    if [ ! -z "$containers" ]; then
        echo "  🗑️  남은 컨테이너 강제 제거..."
        docker rm -f $containers
    else
        echo "  ✅ 제거할 컨테이너 없음"
    fi

    # 5. 네트워크 정리 (사용 중이 아닐 경우)
    echo ""
    echo "🌐 네트워크 정리..."
    networks=("elastic" "rtw-tool_elastic")
    for network in "${networks[@]}"; do
        if docker network ls | grep -q "$network"; then
            docker network rm "$network" 2>/dev/null && \
                echo "  ✅ '$network' 네트워크 제거됨" || \
                echo "  ℹ️  '$network' 네트워크 사용 중 (정상)"
        fi
    done

    # 6. 이미지 캐시 정리 옵션 제공
    echo ""
    read -p "🐳 Docker 이미지도 삭제하시겠습니까? (y/N): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🗑️  관련 Docker 이미지 삭제 중..."
        images=$(docker images --format "{{.Repository}}:{{.Tag}}" | \
            grep -E "(elasticsearch|kibana|kafka|kafka-ui|mongo|mongo-express|redis|prometheus|grafana)")

        if [ ! -z "$images" ]; then
            echo "$images" | while read image; do
                echo "  🗑️  이미지 삭제: $image"
                docker rmi "$image" 2>/dev/null || echo "    ⚠️  $image 삭제 실패"
            done
        else
            echo "  ✅ 제거할 이미지 없음"
        fi
    else
        echo "  ℹ️  이미지는 보존됩니다 (다음 시작 시 빠른 실행)"
    fi

    # 7. 최종 상태 확인
    echo ""
    echo "🔍 최종 정리 상태 확인:"
    echo ""

    # 볼륨 확인
    echo "📦 볼륨 상태:"
    remaining_volumes=$(docker volume ls -q | grep -E "(elasticsearch|kibana|kafka|mongodb|redis|prometheus|grafana|rtw-tool)")
    if [ -z "$remaining_volumes" ]; then
        echo "  ✅ 모든 데이터 볼륨 제거됨"
    else
        echo "  ⚠️  남은 볼륨:"
        echo "$remaining_volumes" | while read vol; do
            echo "    • $vol"
        done
    fi

    # 컨테이너 확인
    echo ""
    echo "🐳 컨테이너 상태:"
    remaining_containers=$(docker ps -aq \
        --filter "name=elasticsearch" \
        --filter "name=kibana" \
        --filter "name=kafka" \
        --filter "name=mongodb" \
        --filter "name=redis" \
        --filter "name=prometheus" \
        --filter "name=grafana")
    if [ -z "$remaining_containers" ]; then
        echo "  ✅ 모든 관련 컨테이너 제거됨"
    else
        echo "  ⚠️  남은 컨테이너: $remaining_containers"
        docker ps -a --filter id="$remaining_containers" --format "    • {{.Names}} ({{.Status}})"
    fi

    # 네트워크 확인
    echo ""
    echo "🌐 네트워크 상태:"
    if docker network ls | grep -qE "(elastic|rtw-tool)"; then
        echo "  ℹ️  네트워크가 남아있습니다 (자동 정리됨)"
        docker network ls | grep -E "(elastic|rtw-tool)" | awk '{print "    • " $2}'
    else
        echo "  ✅ 모든 네트워크 정리됨"
    fi

    # Kafka 로컬 데이터 확인
    echo ""
    echo "📁 로컬 데이터 상태:"
    if [ -d "/Users/usmin/kafka-data" ]; then
        kafka_remaining=$(find /Users/usmin/kafka-data -type f 2>/dev/null | wc -l | tr -d ' ')
        if [ "$kafka_remaining" -eq 0 ]; then
            echo "  ✅ Kafka 데이터 모두 삭제됨"
        else
            echo "  ⚠️  남아있는 Kafka 파일: ${kafka_remaining}개"
        fi
    else
        echo "  ✅ Kafka 디렉토리 없음"
    fi

    echo ""
    echo "🎉 데이터 초기화 완료!"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo "📋 다음 단계:"
    echo "  1. './start.sh'를 실행하여 새로 시작"
    echo "  2. Nori 플러그인이 자동으로 재설치됩니다"
    echo "  3. Kafka 기본 토픽들이 자동으로 생성됩니다"
    echo "  4. MongoDB 컬렉션 및 인덱스가 자동으로 생성됩니다"
    echo "  5. Redis는 빈 상태로 시작됩니다"
    echo ""
    echo "💡 참고:"
    echo "  • 모든 인덱스, 대시보드, 메시지, 컬렉션, 캐시가 초기화되었습니다"
    echo "  • Kafka는 KRaft 모드로 실행됩니다 (Zookeeper 불필요)"
    echo "  • MongoDB는 realtime_winnmin 데이터베이스로 초기화됩니다"
    echo "  • Redis 키워드 캐시는 애플리케이션 실행 시 자동 생성됩니다"
    echo "  • 설정 파일들은 보존됩니다:"
    echo "    - ./elasticsearch/config/"
    echo "    - ./kibana/config/"
    echo "    - ./mongodb/init-scripts/"
    echo "    - ./prometheus/prometheus.yml"
    echo "    - ./grafana/provisioning/"
    echo ""
    echo "🔧 서비스 접속 정보:"
    echo "  • Elasticsearch: http://localhost:9200"
    echo "  • Kibana: http://localhost:5601"
    echo "  • Kafka: localhost:9092"
    echo "  • Kafka UI: http://localhost:9080"
    echo "  • MongoDB: mongodb://localhost:27017"
    echo "  • Mongo Express: http://localhost:9081 (admin/admin123)"
    echo "  • Redis: localhost:6379"
    echo "  • Redis Insight: http://localhost:5540"
    echo "  • Prometheus: http://localhost:9090"
    echo "  • Grafana: http://localhost:3000 (admin/admin)"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

else
    echo ""
    echo "❌ 취소되었습니다."
    echo ""
    echo "💡 부분적인 정리가 필요한 경우:"
    echo "  • 서비스만 중지: './stop.sh'"
    echo "  • 특정 볼륨만 삭제: docker volume rm <볼륨명>"
    echo "  • 사용하지 않는 볼륨 정리: docker volume prune"
    echo "  • Kafka 데이터만 삭제: sudo rm -rf /Users/usmin/kafka-data/*"
    echo "  • Redis 데이터만 삭제: docker exec redis redis-cli FLUSHALL"
    echo "  • ES 인덱스만 삭제: curl -X DELETE 'http://localhost:9200/rtw-*'"
    echo ""
    echo "🔍 현재 상태 확인:"
    echo "  • 볼륨 목록: docker volume ls | grep rtw-tool"
    echo "  • 실행 중인 컨테이너: docker ps"
    echo "  • Kafka 데이터 크기: du -sh /Users/usmin/kafka-data"
    echo "  • Redis 키 개수: docker exec redis redis-cli DBSIZE"
    echo "  • ES 인덱스 목록: curl 'http://localhost:9200/_cat/indices?v'"
fi
