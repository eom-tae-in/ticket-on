package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Component;

/**
 * 주최자 저장소(organizer — V8) 어댑터.
 * <p>
 * 이름과 상태를 저장한다. 새 주최자는 심사 중으로만 만들어지고({@link Organizer#underReview}) 공개 정보(소개 · SNS 링크 · 로고)가 비어
 * 있다. 공개 정보는 공개 정보 편집(#29)이 칸과 함께 저장을 더한다.
 */
@Component
public class OrganizerPersistenceAdapter implements OrganizerRepository {

    private final DSLContext dsl;

    public OrganizerPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Organizer add(Organizer newOrganizer) {
        if (newOrganizer.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 주최자입니다: " + newOrganizer.id().getAsLong());
        }
        long id = dsl.insertInto(ORGANIZER)
                .set(ORGANIZER.NAME, newOrganizer.name().value())
                .set(ORGANIZER.STATUS, newOrganizer.status().name())
                .returningResult(ORGANIZER.ID)
                .fetchSingle()
                .value1();
        return Organizer.restore(id, newOrganizer.name(), newOrganizer.status(), newOrganizer.publicProfile());
    }
}
