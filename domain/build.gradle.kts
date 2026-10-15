// 안쪽 · 엔티티 (핵심 규칙)
// Spring, jOOQ, 그 밖의 프레임워크 의존을 절대 추가하지 않는다. (docs/16-architecture.md)
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":common"))

    // 로고 그림 규칙 테스트에서 그 크기·형식의 그림 파일을 만든다 (TestImages)
    testImplementation(testFixtures(project(":common")))
}
