package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_CATEGORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.domain.event.EventCategory;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventType;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V19 정식 카테고리)을 올리고, 마이그레이션이 넣은 처음 목록이 도메인의 처음 목록(docs/03 "카테고리" 표 그대로)과
 * 같은지, 한 유형 안의 이름이 겹치지 않는지 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("정식 카테고리 저장소 (진짜 PostgreSQL)")
class EventCategoryPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @Test
    @DisplayName("마이그레이션이 넣은 처음 목록은 도메인의 처음 목록과 같다 — 유형마다 같은 이름이 같은 순서로, \"기타\"는 없다")
    void seedMatchesInitialCatalog() {
        EventCategoryCatalog loaded = new EventCategoryPersistenceAdapter(dsl).loadCatalog();

        assertThat(loaded).isEqualTo(EventCategoryCatalog.initial());
        assertThat(loaded.categoriesOf(EventType.PERFORMANCE)).extracting(category -> category.name().value())
                .containsExactly("연극", "뮤지컬", "콘서트", "클래식", "국악", "무용", "코미디");
        assertThat(loaded.categoriesOf(EventType.EXHIBITION)).extracting(category -> category.name().value())
                .containsExactly("회화", "사진", "미디어아트", "설치", "공예");
        assertThat(loaded.categoriesOf(EventType.FESTIVAL)).extracting(category -> category.name().value())
                .containsExactly("음악", "푸드", "문화", "지역축제");
        assertThat(loaded.categories()).extracting(EventCategory::name).noneMatch(name -> name.value().equals("기타"));
    }

    @Test
    @DisplayName("한 유형 안에 같은 이름은 넣을 수 없고, 다른 유형에는 같은 이름을 넣을 수 있다 — (유형, 이름) 유일 색인. 표는 \"기타\"를 받지 않는다")
    void typeAndNameAreUnique() {
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.insertInto(EVENT_CATEGORY)
                .set(EVENT_CATEGORY.EVENT_TYPE, "PERFORMANCE")
                .set(EVENT_CATEGORY.NAME, "연극")
                .set(EVENT_CATEGORY.DISPLAY_ORDER, 7)
                .execute());
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.insertInto(EVENT_CATEGORY)
                .set(EVENT_CATEGORY.EVENT_TYPE, "FESTIVAL")
                .set(EVENT_CATEGORY.NAME, "기타")
                .set(EVENT_CATEGORY.DISPLAY_ORDER, 4)
                .execute());

        long promoted = dsl.insertInto(EVENT_CATEGORY)
                .set(EVENT_CATEGORY.EVENT_TYPE, "EXHIBITION")
                .set(EVENT_CATEGORY.NAME, "음악")
                .set(EVENT_CATEGORY.DISPLAY_ORDER, 5)
                .returningResult(EVENT_CATEGORY.ID)
                .fetchSingle()
                .value1();
        try {
            EventCategoryCatalog withPromoted = new EventCategoryPersistenceAdapter(dsl).loadCatalog();

            assertThat(withPromoted.categoriesOf(EventType.EXHIBITION)).last()
                    .isEqualTo(EventCategory.of(EventType.EXHIBITION, "음악"));
            assertThat(withPromoted.categoriesOf(EventType.FESTIVAL)).first()
                    .isEqualTo(EventCategory.of(EventType.FESTIVAL, "음악"));
        } finally {
            dsl.deleteFrom(EVENT_CATEGORY).where(EVENT_CATEGORY.ID.eq(promoted)).execute();
        }
    }
}
