// 실행 모듈(app-api, app-batch) 공통 설정
plugins {
    id("ticketon.java-conventions")
    id("org.springframework.boot")
}

dependencies {
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}
