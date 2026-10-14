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
    // 로그인 — 세션 쿠키 + DB 저장 세션 (docs/16). 카카오·구글 OAuth2 클라이언트는 #17에서 추가한다
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.springdoc.webmvc.ui)

    // 나가는 어댑터는 실행할 때만 묶는다 → 컨트롤러 코드에서 import할 수 없음
    runtimeOnly(project(":infra-persistence"))
    runtimeOnly(project(":infra-payment"))
    runtimeOnly(project(":infra-message"))
    runtimeOnly(project(":infra-storage"))

    // 통합 테스트에서 Clock 빈을 고정·이동 가능한 시계로 바꾼다 (MutableClock)
    testImplementation(testFixtures(project(":common")))
    // 컨트롤러 슬라이스 테스트 (@WebMvcTest, MockMvc) — Spring Boot 4부터 웹 테스트 지원이 별도 모듈
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // 보안 필터를 건 MockMvc 테스트 (로그인 흉내, CSRF 토큰) — 웹 테스트처럼 Spring Boot 4부터 별도 모듈
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
