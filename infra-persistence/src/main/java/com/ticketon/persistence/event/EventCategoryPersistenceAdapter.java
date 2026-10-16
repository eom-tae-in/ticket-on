package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_CATEGORY;

import com.ticketon.application.event.port.out.EventCategoryRepository;
import com.ticketon.domain.event.EventCategory;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventCategoryName;
import com.ticketon.domain.event.EventType;
import java.util.Comparator;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 정식 카테고리 저장소(event_category — V19) 어댑터. 처음 목록은 마이그레이션이 docs/03 표 그대로 넣어 두었다.
 * <p>
 * 목록은 유형 차례(공연 · 전시 · 페스티벌 — {@link EventType} 차례)로, 같은 유형 안에서는 보여 줄 순서(display_order)대로 담는다 — 처음 목록
 * ({@link EventCategoryCatalog#initial})과 같은 꼴이다.
 */
@Component
public class EventCategoryPersistenceAdapter implements EventCategoryRepository {

    private static final Comparator<EventCategory> BY_TYPE = Comparator.comparing(EventCategory::type);

    private final DSLContext dsl;

    public EventCategoryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EventCategoryCatalog loadCatalog() {
        List<EventCategory> categories = dsl.select(EVENT_CATEGORY.EVENT_TYPE, EVENT_CATEGORY.NAME)
                .from(EVENT_CATEGORY)
                .orderBy(EVENT_CATEGORY.DISPLAY_ORDER, EVENT_CATEGORY.ID)
                .fetch(row -> new EventCategory(EventType.valueOf(row.value1()), new EventCategoryName(row.value2())))
                .stream()
                .sorted(BY_TYPE)
                .toList();
        return new EventCategoryCatalog(categories);
    }
}
