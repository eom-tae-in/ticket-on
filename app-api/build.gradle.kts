// 들어오는 어댑터 · 실행 모듈 — 컨트롤러, Request/Response, 토스 웹훅 수신
plugins {
    id("ticketon.spring-app-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation(libs.springdoc.webmvc.ui)
    // 로그인 구현 때 추가 (docs/16): spring-boot-starter-security, spring-boot-starter-security-oauth2-client

    // 나가는 어댑터는 실행할 때만 묶는다 → 컨트롤러 코드에서 import할 수 없음
    runtimeOnly(project(":infra-persistence"))
    runtimeOnly(project(":infra-payment"))
    runtimeOnly(project(":infra-message"))
    runtimeOnly(project(":infra-storage"))
}
