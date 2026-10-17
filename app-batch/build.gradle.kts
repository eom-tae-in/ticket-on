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
    // 작업 통합 테스트를 진짜 PostgreSQL로 돌린다 (Docker가 없으면 건너뜀). API 서버의 통합 테스트와 같은 라이브러리다
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // 테스트가 표를 바로 읽고 넣고, 잠금을 쥔 트랜잭션을 연다 (JdbcTemplate, TransactionTemplate). 실행할 때는 infra-persistence가 이미 묶는다
    testImplementation("org.springframework:spring-jdbc")
}
