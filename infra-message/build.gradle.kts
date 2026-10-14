// 나가는 어댑터 · 문자·이메일 (실제 발송 / 기록만 — 업체는 배포 때 정함)
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation("org.springframework:spring-context")
}
