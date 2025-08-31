plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    // 🌐 웹 프레임워크 & 핵심
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // 📊 모니터링 & 운영
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 🔍 Elasticsearch (Spring Boot 3.5.3 호환 버전)
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

    // 🔧 Kotlin 지원
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
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
