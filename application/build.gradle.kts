// 안쪽 · 유스케이스 — 포트(port/in, port/out)와 서비스
// 트랜잭션과 빈 등록에 필요한 Spring 최소 의존만 허용. 어댑터(infra-*, app-*)를 의존하지 않는다.
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")

    testImplementation(libs.archunit.junit5)
    // 서비스 테스트에서 시각·생성 값을 고정한다 (MutableClock, FixedTokenGenerator)
    testImplementation(testFixtures(project(":common")))
}
