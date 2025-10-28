// 파일: rtw-tool/mongodb/init-scripts/01-init-db.js

// realtime_winnmin 데이터베이스로 전환
db = db.getSiblingDB('realtime_winnmin');

print('🔧 MongoDB 초기화 시작...');

// ===== 1. 애플리케이션 사용자 생성 =====
try {
    db.createUser({
        user: 'rtw_app',
        pwd: 'rtw_password123',
        roles: [
            {
                role: 'readWrite',
                db: 'realtime_winnmin'
            }
        ]
    });
    print('✅ 애플리케이션 사용자 (rtw_app) 생성 완료');
} catch (e) {
    if (e.code === 51003) {
        print('ℹ️  사용자 rtw_app이 이미 존재합니다');
    } else {
        print('❌ 사용자 생성 실패: ' + e.message);
    }
}

// ===== 2. 컬렉션 생성 =====
print('');
print('📦 컬렉션 생성 중...');

// 2-1. origin_data 컬렉션
try {
    db.createCollection('origin_data');
    print('✅ origin_data 컬렉션 생성 완료');
} catch (e) {
    if (e.code === 48) {
        print('ℹ️  컬렉션 origin_data가 이미 존재합니다');
    } else {
        print('❌ origin_data 컬렉션 생성 실패: ' + e.message);
    }
}

// 2-2. full_text_data 컬렉션 (NEW!)
try {
    db.createCollection('full_text_data');
    print('✅ full_text_data 컬렉션 생성 완료');
} catch (e) {
    if (e.code === 48) {
        print('ℹ️  컬렉션 full_text_data가 이미 존재합니다');
    } else {
        print('❌ full_text_data 컬렉션 생성 실패: ' + e.message);
    }
}

// ===== 3. 인덱스 정보 안내 =====
print('');
print('ℹ️  인덱스는 애플리케이션 시작 시 자동으로 생성됩니다');
print('');
print('📊 origin_data 컬렉션 인덱스:');
print('   - _id (자동 생성)');
print('   - idx_source (source)');
print('   - idx_createdAt_source (createdAt DESC, source ASC)');
print('   - idx_ttl_createdAt (TTL: 30일 자동 삭제)');
print('');
print('📊 full_text_data 컬렉션 인덱스:');
print('   - _id (자동 생성)');
print('   - idx_refinedId (refinedId UNIQUE)');
print('   - idx_originId (originId)');
print('   - idx_sourceType_createdAt (sourceType, createdAt DESC)');
print('   - idx_ttl_createdAt (TTL: 30일 자동 삭제)');

print('');
print('🎉 MongoDB 초기화 완료!');
print('');
print('📊 생성된 리소스:');
print('   • 데이터베이스: realtime_winnmin');
print('   • 사용자: rtw_app (readWrite)');
print('   • 컬렉션: origin_data, full_text_data');
print('');
print('🔐 접속 정보:');
print('   • URI: mongodb://rtw_app:rtw_password123@localhost:27017/realtime_winnmin');
print('   • 관리자: mongodb://admin:admin123@localhost:27017');
print('');
print('💡 아키텍처 설명:');
print('   • origin_data: 원본 데이터 저장 (rtw-collector)');
print('   • full_text_data: 풀텍스트 추출 결과 저장 (rtw-dataclean)');
print('   • Kafka에는 ID와 메타데이터만 전송하여 메시지 크기 최소화');
