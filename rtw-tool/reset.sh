#!/bin/bash

echo "🗑️  Elasticsearch + Kibana + Kafka 데이터 완전 초기화"
echo "⚠️  주의: 모든 저장된 데이터가 삭제됩니다!"
echo ""
echo "삭제될 데이터:"
echo "  📊 Elasticsearch 인덱스 및 설정"
echo "  📈 Kibana 대시보드 및 설정"
echo "  📝 Kafka 토픽 및 메시지"
echo "  🏛️  Zookeeper 메타데이터"
echo ""

read -p "정말로 모든 데이터를 삭제하시겠습니까? (y/N): " -n 1 -r
echo

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "🛑 모든 서비스 중지 및 데이터 삭제 중..."

    # 1. 컨테이너 중지 및 볼륨까지 모두 삭제
    echo "🏃 Docker Compose 서비스 및 볼륨 삭제..."
    docker-compose down -v

    # 2. 개별 볼륨이 남아있는지 확인하고 삭제
    echo "🔍 남아있는 관련 볼륨 확인 및 삭제..."

    volumes=("elasticsearch_data" "kibana_data" "kafka_data" "zookeeper_data" "zookeeper_logs")

    for volume in "${volumes[@]}"; do
        if docker volume ls -q | grep -q "^${volume}$"; then
            echo "  🗑️  볼륨 삭제: $volume"
            docker volume rm "$volume" 2>/dev/null || echo "    ⚠️  $volume 삭제 실패 (사용 중일 수 있음)"
        else
            echo "  ✅ 볼륨 없음: $volume"
        fi
    done

    # 3. 관련 컨테이너가 완전히 제거되었는지 확인
    echo ""
    echo "🧹 남아있는 컨테이너 정리..."
    containers=$(docker ps -aq --filter "name=elasticsearch" --filter "name=kibana" --filter "name=kafka" --filter "name=zookeeper" --filter "name=kafka-ui")

    if [ ! -z "$containers" ]; then
        echo "  🗑️  남은 컨테이너 강제 제거..."
        docker rm -f $containers
    else
        echo "  ✅ 제거할 컨테이너 없음"
    fi

    # 4. 네트워크 정리 (사용 중이 아닐 경우)
    echo ""
    echo "🌐 네트워크 정리..."
    if docker network ls | grep -q "elastic"; then
        docker network rm elastic 2>/dev/null && echo "  ✅ 'elastic' 네트워크 제거됨" || echo "  ℹ️  'elastic' 네트워크 사용 중 (정상)"
    else
        echo "  ✅ 제거할 네트워크 없음"
    fi

    # 5. 이미지 캐시 정리 옵션 제공
    echo ""
    read -p "🐳 Docker 이미지도 삭제하시겠습니까? (y/N): " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "🗑️  관련 Docker 이미지 삭제 중..."
        images=$(docker images --format "{{.Repository}}:{{.Tag}}" | grep -E "(elasticsearch|kibana|kafka|zookeeper|kafka-ui)")

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

    # 6. 최종 상태 확인
    echo ""
    echo "🔍 최종 정리 상태 확인:"

    # 볼륨 확인
    remaining_volumes=$(docker volume ls -q | grep -E "(elasticsearch|kibana|kafka)")
    if [ -z "$remaining_volumes" ]; then
        echo "  ✅ 모든 데이터 볼륨 제거됨"
    else
        echo "  ⚠️  남은 볼륨: $remaining_volumes"
    fi

    # 컨테이너 확인
    remaining_containers=$(docker ps -aq --filter "name=elasticsearch" --filter "name=kibana" --filter "name=kafka" --filter "name=kafka-ui")
    if [ -z "$remaining_containers" ]; then
        echo "  ✅ 모든 관련 컨테이너 제거됨"
    else
        echo "  ⚠️  남은 컨테이너: $remaining_containers"
    fi

    echo ""
    echo "🎉 데이터 초기화 완료!"
    echo ""
    echo "📋 다음 단계:"
    echo "  1. './start.sh'를 실행하여 새로 시작"
    echo "  2. Nori 플러그인이 자동으로 재설치됩니다"
    echo "  3. Kafka 기본 토픽들이 자동으로 생성됩니다 (KRaft 모드)"
    echo ""
    echo "💡 참고:"
    echo "  • 모든 인덱스, 대시보드, 메시지가 초기화되었습니다"
    echo "  • Kafka는 KRaft 모드로 실행됩니다 (Zookeeper 불필요)"
    echo "  • 설정 파일들은 보존됩니다 (./elasticsearch/config/, ./kibana/config/)"

else
    echo ""
    echo "❌ 취소되었습니다."
    echo "💡 부분적인 정리가 필요한 경우:"
    echo "  • 서비스만 중지: './stop.sh'"
    echo "  • 특정 볼륨만 삭제: docker volume rm <볼륨명>"
    echo "  • 사용하지 않는 볼륨 정리: docker volume prune"
fi
