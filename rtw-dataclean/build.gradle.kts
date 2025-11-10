plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // rtw-core 의존성
    implementation(project(":rtw-core"))

    // Apache Tika
    implementation("org.apache.tika:tika-core:2.9.1")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.1")
    implementation("org.apache.commons:commons-text:1.10.0")

    // Bliki - 위키마크업 파싱 (Wikipedia 공식 라이브러리)
    implementation("info.bliki.wiki:bliki-core:3.1.0")
}
