// 모든 자바 모듈의 공통 설정
plugins {
    java
}

group = "com.ticketon"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val libs = the<VersionCatalogsExtension>().named("libs")
val bom = libs.findLibrary("spring-boot-dependencies").get().get()

dependencies {
    // 버전만 맞춰 주는 BOM. 의존성을 추가하지는 않으므로 domain도 순수하게 유지됨
    "implementation"(platform(bom))
    "testImplementation"(platform(bom))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testImplementation"("org.assertj:assertj-core")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
