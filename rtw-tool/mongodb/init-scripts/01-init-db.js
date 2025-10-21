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
try {
    db.createCollection('origin_data');
    print('✅ origin_data 컬렉션 생성 완료');
} catch (e) {
    if (e.code === 48) {
        print('ℹ️  컬렉션 origin_data가 이미 존재합니다');
    } else {
        print('❌ 컬렉션 생성 실패: ' + e.message);
    }
}

// ===== 3. 기본 인덱스만 생성 (_id는 자동 생성됨) =====
print('');
print('ℹ️  인덱스는 애플리케이션 시작 시 자동으로 생성됩니다');
print('   - MongoConfig.kt에서 관리됨');
print('   - TTL 인덱스 포함 (30일 자동 삭제)');

print('');
print('🎉 MongoDB 초기화 완료!');
print('');
print('📊 생성된 리소스:');
print('   • 데이터베이스: realtime_winnmin');
print('   • 사용자: rtw_app (readWrite)');
print('   • 컬렉션: origin_data');
print('');
print('🔐 접속 정보:');
print('   • URI: mongodb://rtw_app:rtw_password123@localhost:27017/realtime_winnmin');
print('   • 관리자: mongodb://admin:admin123@localhost:27017');
