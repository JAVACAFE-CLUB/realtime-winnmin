plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.3")
    }
}

dependencies {
    // ===== Kotlin 핵심 =====
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("org.jetbrains.kotlin:kotlin-stdlib")

    // ===== Coroutines =====
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // ===== Jackson =====
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ===== Spring Core =====
    api("org.springframework:spring-context")
    api("org.springframework.boot:spring-boot-autoconfigure")

    // ===== Spring Web & AOP =====
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-aop")

    // ===== MongoDB 의존성 =====
    api("org.springframework.boot:spring-boot-starter-data-mongodb")

    // ===== Kafka =====
    api("org.springframework.kafka:spring-kafka")

    // ===== 모니터링 =====
    api("org.springframework.boot:spring-boot-starter-actuator")

    // ===== 로깅 =====
    api("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // ===== 개발 도구 =====
    compileOnly("org.springframework.boot:spring-boot-devtools")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")

    // ===== 테스트 =====
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
