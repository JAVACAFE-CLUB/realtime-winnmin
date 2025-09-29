plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // ===== 공통 모듈 =====
    implementation(project(":rtw-core"))

    // ===== Collector 전용: XML/HTML 파싱 =====
    implementation("com.fasterxml.woodstox:woodstox-core:6.5.1")
    implementation("org.codehaus.woodstox:stax2-api:4.2.1")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.8")

    // ===== 개발 도구 (런타임 필요) =====
    // rtw-core에서 compileOnly이므로 런타임 환경에서 필요
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
