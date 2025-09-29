plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// Gradle Wrapper 설정
tasks.wrapper {
    gradleVersion = "8.10"
    distributionType = Wrapper.DistributionType.BIN
}


group = "com.javacafe"
version = "0.0.1-SNAPSHOT"

// 모든 서브모듈에 공통 적용될 설정
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")

    group = rootProject.group
    version = rootProject.version

    // Java 툴체인 설정
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    configurations {
        val compileOnly by getting {
            extendsFrom(configurations.getByName("annotationProcessor"))
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        // 모든 모듈에서 공통으로 사용할 기본 의존성들
    }

    // Kotlin 컴파일러 옵션 설정
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
