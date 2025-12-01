plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// Elasticsearch 버전 통일 (Spring Boot 3.2.x와 호환)
val elasticsearchVersion = "8.11.4"

dependencies {
    // ===== rtw-core 의존성 (공유 모델) =====
    implementation(project(":rtw-core"))
    
    // 🌐 웹 프레임워크 & 핵심
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 📊 모니터링 & 운영
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 🔍 Elasticsearch
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("co.elastic.clients:elasticsearch-java:$elasticsearchVersion")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    
    // 🗄️ Redis (키워드 캐시 조회)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // 🔧 Kotlin 지원
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // ⚙️ 개발 도구
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // 🧪 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
