// 나가는 어댑터 · 토스페이먼츠 (application의 결제 포트를 구현)
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation("org.springframework:spring-context")
}
