package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 주최자 저장소(organizer — V8) 어댑터.
 * <p>
 * 이름과 상태를 저장한다. 새 주최자는 심사 중으로만 만들어지고({@link Organizer#underReview}) 공개 정보(소개 · SNS 링크 · 로고)가 비어
 * 있다. 공개 정보는 공개 정보 편집(#29)이 칸과 함께 저장을 더한다 — 그때까지 읽은 주최자의 공개 정보는 비어 있다.
 * <p>
 * 승인 저장은 {@code UPDATE … WHERE status = 'UNDER_REVIEW'}로 한다. 바뀐 줄이 없으면 읽은 뒤 다른 처리가 먼저 상태를 바꾼 것이라 충돌이다.
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

    @Override
    public Optional<Organizer> findById(long id) {
        return dsl.selectFrom(ORGANIZER)
                .where(ORGANIZER.ID.eq(id))
                .fetchOptional()
                .map(organizer -> Organizer.restore(organizer.getId(), new OrganizerName(organizer.getName()),
                        OrganizerStatus.valueOf(organizer.getStatus()), OrganizerPublicProfile.empty()));
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer) {
        long id = approvedOrganizer.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 주최자는 승인을 저장할 수 없습니다"));
        if (approvedOrganizer.status() != OrganizerStatus.ACTIVE) {
            throw new IllegalArgumentException("승인한 주최자가 아닙니다: 주최자 " + id + ", 상태 " + approvedOrganizer.status());
        }
        int updated = dsl.update(ORGANIZER)
                .set(ORGANIZER.STATUS, OrganizerStatus.ACTIVE.name())
                .where(ORGANIZER.ID.eq(id))
                .and(ORGANIZER.STATUS.eq(OrganizerStatus.UNDER_REVIEW.name()))
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW,
                    "승인 경합 — 주최자 " + id + "가 이미 심사 중이 아님");
        }
    }
}
