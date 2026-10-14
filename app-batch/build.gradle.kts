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
}
