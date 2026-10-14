package com.ticketon.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;

/**
 * docs/16-architecture.md의 구조 규칙을 기계적으로 검사한다.
 * <p>
 * 모듈 사이의 방향(domain ← application ← 어댑터)은 Gradle 빌드가 막고,
 * 여기서는 빌드가 못 막는 것 — 패키지 사이의 규칙 — 을 검사한다.
 * 이 테스트가 실패하면 테스트를 고치지 말고 코드를 고친다.
 */
@AnalyzeClasses(packages = "com.ticketon", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 도메인은 순수 자바. 프레임워크도, 유스케이스도 모른다. */
    @ArchTest
    static final ArchRule domainIsPure = noClasses()
            .that().resideInAPackage("com.ticketon.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.jooq..",
                    "jakarta.persistence..",
                    "com.ticketon.application..")
            .allowEmptyShould(true);

    /** 유스케이스는 어댑터를 모른다. 바깥과는 포트로만 이어진다. */
    @ArchTest
    static final ArchRule applicationDoesNotKnowAdapters = noClasses()
            .that().resideInAPackage("com.ticketon.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.ticketon.api..",
                    "com.ticketon.batch..",
                    "com.ticketon.persistence..",
                    "com.ticketon.payment..",
                    "com.ticketon.message..",
                    "com.ticketon.storage..",
                    "org.jooq..")
            .allowEmptyShould(true);

    /** 나가는 포트는 인터페이스만. 구현은 infra-* 어댑터에 둔다. */
    @ArchTest
    static final ArchRule outgoingPortsAreInterfaces = classes()
            .that().resideInAPackage("com.ticketon.application..port.out..")
            .should().beInterfaces()
            .allowEmptyShould(true);

    /**
     * 도메인 패키지는 위 층에서 아래 층으로만 부른다 (docs/16 "도메인 패키지와 순서").
     * 아래 층이 위에 알려야 하면 이벤트로 알리고, 직접 부르지 않는다.
     */
    @ArchTest
    static final ArchRule domainPackagesFollowLayerOrder = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("L5").definedBy(packagesOf("admin", "stats", "settlement"))
            .layer("L4").definedBy(packagesOf("eventchange"))
            .layer("L3").definedBy(packagesOf("booking", "invitation", "checkin"))
            .layer("L2").definedBy(packagesOf("inventory", "pricing", "ticket", "payment"))
            .layer("L1").definedBy(packagesOf("event"))
            .layer("L0").definedBy(packagesOf("account", "organizer", "notification"))
            .whereLayer("L5").mayNotBeAccessedByAnyLayer()
            .whereLayer("L4").mayOnlyBeAccessedByLayers("L5")
            .whereLayer("L3").mayOnlyBeAccessedByLayers("L4", "L5")
            .whereLayer("L2").mayOnlyBeAccessedByLayers("L3", "L4", "L5")
            .whereLayer("L1").mayOnlyBeAccessedByLayers("L2", "L3", "L4", "L5")
            .whereLayer("L0").mayOnlyBeAccessedByLayers("L1", "L2", "L3", "L4", "L5")
            .allowEmptyShould(true);

    private static String[] packagesOf(String... names) {
        return Arrays.stream(names)
                .flatMap(n -> Arrays.stream(new String[] {
                        "com.ticketon.domain." + n + "..",
                        "com.ticketon.application." + n + ".."}))
                .toArray(String[]::new);
    }
}
