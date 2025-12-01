#!/bin/bash
# ============================================
# Elasticsearch 인덱스 초기화 스크립트
# ============================================

ES_HOST="${ES_HOST:-localhost}"
ES_PORT="${ES_PORT:-9200}"
ES_URL="http://${ES_HOST}:${ES_PORT}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================"
echo "Elasticsearch 인덱스 초기화"
echo "ES URL: ${ES_URL}"
echo "============================================"

# Elasticsearch 연결 대기
echo "⏳ Elasticsearch 연결 대기 중..."
until curl -s "${ES_URL}/_cluster/health" > /dev/null 2>&1; do
    echo "   Elasticsearch가 아직 준비되지 않았습니다. 5초 후 재시도..."
    sleep 5
done
echo "✅ Elasticsearch 연결 성공!"

# Nori 플러그인 확인
echo ""
echo "🔍 Nori 플러그인 확인..."
PLUGINS=$(curl -s "${ES_URL}/_cat/plugins?format=json")
if echo "$PLUGINS" | grep -q "analysis-nori"; then
    echo "✅ analysis-nori 플러그인이 설치되어 있습니다."
else
    echo "❌ analysis-nori 플러그인이 설치되어 있지 않습니다!"
    echo "   Dockerfile을 확인하고 ES 컨테이너를 다시 빌드하세요."
    exit 1
fi

# rtw-articles 인덱스 생성
echo ""
echo "📦 rtw-articles 인덱스 생성..."
ARTICLE_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" "${ES_URL}/rtw-articles")
if [ "$ARTICLE_EXISTS" = "200" ]; then
    echo "   ⚠️  rtw-articles 인덱스가 이미 존재합니다."
    read -p "   삭제하고 다시 생성하시겠습니까? (y/N): " CONFIRM
    if [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
        echo "   🗑️  기존 인덱스 삭제 중..."
        curl -s -X DELETE "${ES_URL}/rtw-articles" > /dev/null
    else
        echo "   ⏭️  rtw-articles 인덱스 생성 건너뜀"
    fi
fi

if [ "$ARTICLE_EXISTS" != "200" ] || [ "$CONFIRM" = "y" ] || [ "$CONFIRM" = "Y" ]; then
    RESULT=$(curl -s -X PUT "${ES_URL}/rtw-articles" \
        -H "Content-Type: application/json" \
        -d @"${SCRIPT_DIR}/article-index-settings.json")
    
    if echo "$RESULT" | grep -q '"acknowledged":true'; then
        echo "   ✅ rtw-articles 인덱스 생성 완료!"
    else
        echo "   ❌ rtw-articles 인덱스 생성 실패: $RESULT"
    fi
fi

# rtw-keywords 인덱스 생성
echo ""
echo "📦 rtw-keywords 인덱스 생성..."
KEYWORD_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" "${ES_URL}/rtw-keywords")
if [ "$KEYWORD_EXISTS" = "200" ]; then
    echo "   ⚠️  rtw-keywords 인덱스가 이미 존재합니다."
    read -p "   삭제하고 다시 생성하시겠습니까? (y/N): " CONFIRM2
    if [ "$CONFIRM2" = "y" ] || [ "$CONFIRM2" = "Y" ]; then
        echo "   🗑️  기존 인덱스 삭제 중..."
        curl -s -X DELETE "${ES_URL}/rtw-keywords" > /dev/null
    else
        echo "   ⏭️  rtw-keywords 인덱스 생성 건너뜀"
    fi
fi

if [ "$KEYWORD_EXISTS" != "200" ] || [ "$CONFIRM2" = "y" ] || [ "$CONFIRM2" = "Y" ]; then
    RESULT2=$(curl -s -X PUT "${ES_URL}/rtw-keywords" \
        -H "Content-Type: application/json" \
        -d @"${SCRIPT_DIR}/keyword-index-settings.json")
    
    if echo "$RESULT2" | grep -q '"acknowledged":true'; then
        echo "   ✅ rtw-keywords 인덱스 생성 완료!"
    else
        echo "   ❌ rtw-keywords 인덱스 생성 실패: $RESULT2"
    fi
fi

# 결과 확인
echo ""
echo "============================================"
echo "📊 인덱스 목록 확인"
echo "============================================"
curl -s "${ES_URL}/_cat/indices?v&index=rtw-*"

echo ""
echo "============================================"
echo "🔬 분석기 테스트 (korean_noun_only)"
echo "============================================"
echo "테스트 문장: '삼성전자가 인공지능 기술을 발표했습니다.'"
curl -s -X POST "${ES_URL}/rtw-articles/_analyze" \
    -H "Content-Type: application/json" \
    -d '{
        "analyzer": "korean_noun_only",
        "text": "삼성전자가 인공지능 기술을 발표했습니다."
    }' | python3 -c "import sys, json; tokens = json.load(sys.stdin)['tokens']; print('추출된 명사:', [t['token'] for t in tokens])" 2>/dev/null || \
curl -s -X POST "${ES_URL}/rtw-articles/_analyze" \
    -H "Content-Type: application/json" \
    -d '{
        "analyzer": "korean_noun_only",
        "text": "삼성전자가 인공지능 기술을 발표했습니다."
    }'

echo ""
echo "✅ 초기화 완료!"
