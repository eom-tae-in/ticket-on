package com.ticketon.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 마이그레이션 전체가 진짜 PostgreSQL에서 끝까지 도는지 확인한다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationTest {

    @Test
    @DisplayName("모든 마이그레이션이 실제 PostgreSQL에서 성공한다")
    void migrationsRunOnRealPostgres() {
        var flyway = Flyway.configure()
                .dataSource("jdbc:tc:postgresql:18:///ticketon", "test", "test")
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
    }
}
