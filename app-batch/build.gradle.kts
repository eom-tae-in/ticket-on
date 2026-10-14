// 들어오는 어댑터 · 실행 모듈 — 정해진 시각 작업 (Spring 스케줄러)
plugins {
    id("ticketon.spring-app-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter")

    runtimeOnly(project(":infra-persistence"))
    runtimeOnly(project(":infra-payment"))
    runtimeOnly(project(":infra-message"))
    runtimeOnly(project(":infra-storage"))

    // 통합 테스트에서 Clock 빈을 고정·이동 가능한 시계로 바꾼다 (MutableClock)
    testImplementation(testFixtures(project(":common")))
}
