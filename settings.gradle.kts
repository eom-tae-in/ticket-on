pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // JDK 25가 없으면 Gradle이 자동으로 내려받음
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ticket-on"

// 안쪽
include("domain")
include("application")
include("common")

// 들어오는 어댑터 (실행 모듈)
include("app-api")
include("app-batch")

// 나가는 어댑터
include("infra-persistence")
include("infra-payment")
include("infra-message")
include("infra-storage")
