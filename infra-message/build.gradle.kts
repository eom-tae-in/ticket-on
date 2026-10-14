// 나가는 어댑터 · 문자·이메일 (실제 발송 / 기록만 — 업체는 배포 때 정함)
plugins {
    id("ticketon.java-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))
    implementation("org.springframework:spring-context")
    // '기록만' 어댑터가 남기는 로그. 실행 모듈(Spring Boot)과 같은 로그 API를 쓴다
    implementation("org.slf4j:slf4j-api")

    // 어댑터가 남긴 로그를 테스트에서 붙잡는다 (Spring Boot 기본 로그 구현과 같은 Logback)
    testImplementation("ch.qos.logback:logback-classic")
}
