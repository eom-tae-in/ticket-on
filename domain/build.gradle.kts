// 안쪽 · 엔티티 (핵심 규칙)
// Spring, jOOQ, 그 밖의 프레임워크 의존을 절대 추가하지 않는다. (docs/16-architecture.md)
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":common"))
}
