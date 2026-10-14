// 나가는 어댑터 · DB — jOOQ, 리포지토리 구현, Flyway 마이그레이션, 읽기 전용 조회
plugins {
    id("ticketon.java-conventions")
    alias(libs.plugins.jooq.codegen)
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 마이그레이션 SQL 파일에서 바로 jOOQ 코드를 만든다 (DB 접속 없이)
    jooqCodegen(libs.jooq.meta.extensions)

    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-jdbc")
    testRuntimeOnly("org.postgresql:postgresql")
}

jooq {
    configuration {
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    property {
                        key = "scripts"
                        value = "src/main/resources/db/migration/*.sql"
                    }
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
            }
            target {
                packageName = "com.ticketon.persistence.jooq"
            }
        }
    }
}

// 마이그레이션이 바뀔 때만 코드 생성을 다시 돌린다
tasks.named("jooqCodegen") {
    inputs.files(fileTree("src/main/resources/db/migration"))
}

// 생성한 jOOQ 코드를 main 소스에 넣는다. 태스크 출력을 소스로 잡으므로 컴파일 전에 코드 생성이 먼저 돈다
sourceSets.main {
    java.srcDir(tasks.named("jooqCodegen"))
}
