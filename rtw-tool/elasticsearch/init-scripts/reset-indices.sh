#!/bin/bash
# ============================================
# Elasticsearch 인덱스 리셋 스크립트
# 모든 rtw-* 인덱스를 삭제하고 다시 생성
# ============================================

ES_HOST="${ES_HOST:-localhost}"
ES_PORT="${ES_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================"
echo "⚠️  Elasticsearch 인덱스 리셋"
echo "ES URL: ${ES_URL}"
echo "============================================"
echo ""
echo "경고: 이 작업은 모든 rtw-* 인덱스의 데이터를 삭제합니다!"
read -p "계속하시겠습니까? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "작업이 취소되었습니다."
    exit 0
fi

# 기존 인덱스 삭제
echo ""
echo "🗑️  기존 인덱스 삭제 중..."
curl -s -X DELETE "${ES_URL}/rtw-articles" > /dev/null 2>&1
curl -s -X DELETE "${ES_URL}/rtw-keywords" > /dev/null 2>&1
echo "   ✅ 삭제 완료"

# 잠시 대기
sleep 2

# 인덱스 재생성
echo ""
echo "📦 rtw-articles 인덱스 생성..."
curl -s -X PUT "${ES_URL}/rtw-articles" \
    -H "Content-Type: application/json" \
    -d @"${SCRIPT_DIR}/article-index-settings.json" > /dev/null
echo "   ✅ 완료"

echo ""
echo "📦 rtw-keywords 인덱스 생성..."
curl -s -X PUT "${ES_URL}/rtw-keywords" \
    -H "Content-Type: application/json" \
    -d @"${SCRIPT_DIR}/keyword-index-settings.json" > /dev/null
echo "   ✅ 완료"

# 결과 확인
echo ""
echo "============================================"
echo "📊 인덱스 목록"
echo "============================================"
curl -s "${ES_URL}/_cat/indices?v&index=rtw-*"

echo ""
echo "✅ 리셋 완료!"
