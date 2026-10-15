package com.ticketon.persistence.admin;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.admin.port.out.OrganizerApplicationReviewQueryPort;
import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 주최자 심사 화면의 이력 세탁 대조 어댑터 — 주최자 표(organizer)와 소속 표(staff_membership, V8)에서 같은 소유자 또는 같은 단체명의
 * 닫힌 주최자를 찾는다. 도메인 객체를 만들지 않는다.
 * <p>
 * 같은 소유자는 신청한 사람에게 그 주최자의 소유자 소속이 한 줄이라도 있는지(끝난 소속 포함)로 본다 — 닫히면 운영진이 전원 빠져 소속이 모두
 * 끝나 있다. 같은 단체명은 저장된 주최자명과 신청한 이름이 글자 그대로 같은지로 본다(둘 다 앞뒤 공백을 걷고 NFC로 맞춘 값).
 */
@Component
public class OrganizerApplicationReviewQueryPersistenceAdapter implements OrganizerApplicationReviewQueryPort {

    private final DSLContext dsl;

    public OrganizerApplicationReviewQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<SimilarClosedOrganizer> similarClosedOrganizers(long applicantStaffAccountId, OrganizerName name) {
        Condition ownedByApplicant = DSL.exists(DSL.selectOne()
                .from(STAFF_MEMBERSHIP)
                .where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(ORGANIZER.ID))
                .and(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID.eq(applicantStaffAccountId))
                .and(STAFF_MEMBERSHIP.ROLE.eq(StaffRole.OWNER.name())));
        Condition sameName = ORGANIZER.NAME.eq(name.value());
        return dsl.select(ORGANIZER.ID, ORGANIZER.NAME, DSL.field(ownedByApplicant), DSL.field(sameName))
                .from(ORGANIZER)
                .where(ORGANIZER.STATUS.eq(OrganizerStatus.CLOSED.name()))
                .and(ownedByApplicant.or(sameName))
                .orderBy(ORGANIZER.ID)
                .fetch(row -> new SimilarClosedOrganizer(row.value1(), row.value2(), row.value3(), row.value4()));
    }
}
