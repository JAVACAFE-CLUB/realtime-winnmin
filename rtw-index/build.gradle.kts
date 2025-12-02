plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// Elasticsearch 버전 통일 (Spring Boot 3.2.x와 호환)
val elasticsearchVersion = "8.11.4"

dependencies {
    // ===== rtw-core 의존성 (MongoDB, Kafka, Jackson 등 포함) =====
    implementation(project(":rtw-core"))

    // ===== Elasticsearch =====
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    // ES Java Client 명시적 버전 지정 (Spring Boot BOM 버전과 일치)
    implementation("co.elastic.clients:elasticsearch-java:$elasticsearchVersion")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    
    // ===== Redis (키워드 캐싱) =====
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // ===== Reactive 지원 =====
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // ===== 테스트 =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
