plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    // 🌐 웹 프레임워크 (Reactive)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // 📨 메시징 시스템
    implementation("org.springframework.kafka:spring-kafka")

    // 🔧 JSON 처리
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // ⚡ 코틀린 & 리액티브 지원
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // 🧪 테스트 프레임워크
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // 🧪 리액티브 테스트
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")

    // 🧪 Kafka 테스트
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // ⚙️ 개발 도구
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}