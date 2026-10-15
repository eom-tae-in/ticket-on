// 나가는 어댑터 · 포스터·로고 파일 (개발 중엔 로컬 저장 — 업체는 배포 때 정함)
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation("org.springframework:spring-context")

    // 저장 어댑터 테스트에서 로고로 받을 수 있는 그림 파일을 만든다 (TestImages)
    testImplementation(testFixtures(project(":common")))
}
