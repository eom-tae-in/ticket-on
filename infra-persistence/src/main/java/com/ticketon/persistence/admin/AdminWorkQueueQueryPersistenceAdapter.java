package com.ticketon.persistence.admin;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION;
import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CHANGE;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;

import com.ticketon.application.admin.port.out.AdminWorkQueueQueryPort;
import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.application.admin.query.PendingAdminWork;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.persistence.jooq.tables.StaffAccount;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 관리자 처리 목록 조회 어댑터 — 요청 종류마다 원천 표에서 아직 처리하지 않은 줄을 읽어 한 목록으로 모은다. 도메인 객체를 만들지 않는다.
 * <ul>
 *   <li>주최자 신청 — 신청서 표(organizer_application — V8)의 심사 중인 줄. 제목은 신청한 이름 또는 단체명, 접수 일시는 제출 일시다.
 *       담당자 연락처 · 사업자 정보 · 정산 계좌는 읽지 않는다.</li>
 *   <li>소유자 변경 — 소유자 변경 이력 표(ownership_change — V13)의 확인 전인 줄. 제목은 "주최자명 · 넘긴 사람 → 받은 사람"이고 사람은
 *       공개 프로필의 닉네임으로 적는다(탈퇴해도 닉네임은 남는다). 접수 일시는 넘긴 일시다.</li>
 * </ul>
 * 다른 종류는 그 기능의 이슈가 원천 조회를 하나씩 더해 {@link #pendingWork}에 잇는다.
 */
@Component
public class AdminWorkQueueQueryPersistenceAdapter implements AdminWorkQueueQueryPort {

    private static final Comparator<PendingAdminWork> RECEIVED_ORDER = Comparator
            .comparing(PendingAdminWork::receivedAt)
            .thenComparing(PendingAdminWork::kind)
            .thenComparingLong(PendingAdminWork::targetId);

    private final DSLContext dsl;

    public AdminWorkQueueQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<PendingAdminWork> pendingWork() {
        return Stream.of(pendingOrganizerApplications(), unconfirmedOwnershipChanges())
                .flatMap(List::stream)
                .sorted(RECEIVED_ORDER)
                .toList();
    }

    private List<PendingAdminWork> pendingOrganizerApplications() {
        return dsl.select(ORGANIZER_APPLICATION.ID, ORGANIZER_APPLICATION.NAME, ORGANIZER_APPLICATION.SUBMITTED_AT)
                .from(ORGANIZER_APPLICATION)
                .where(ORGANIZER_APPLICATION.STATUS.eq(OrganizerApplicationStatus.UNDER_REVIEW.name()))
                .fetch(row -> new PendingAdminWork(AdminWorkKind.ORGANIZER_APPLICATION, row.value1(), row.value2(),
                        row.value3().toInstant()));
    }

    private List<PendingAdminWork> unconfirmedOwnershipChanges() {
        StaffAccount previousOwner = STAFF_ACCOUNT.as("previous_owner");
        StaffAccount newOwner = STAFF_ACCOUNT.as("new_owner");
        return dsl.select(OWNERSHIP_CHANGE.ID, ORGANIZER.NAME, previousOwner.NICKNAME, newOwner.NICKNAME,
                        OWNERSHIP_CHANGE.CHANGED_AT)
                .from(OWNERSHIP_CHANGE)
                .join(ORGANIZER).on(ORGANIZER.ID.eq(OWNERSHIP_CHANGE.ORGANIZER_ID))
                .join(previousOwner).on(previousOwner.ID.eq(OWNERSHIP_CHANGE.PREVIOUS_OWNER_STAFF_ACCOUNT_ID))
                .join(newOwner).on(newOwner.ID.eq(OWNERSHIP_CHANGE.NEW_OWNER_STAFF_ACCOUNT_ID))
                .where(OWNERSHIP_CHANGE.CONFIRMED_AT.isNull())
                .fetch(row -> new PendingAdminWork(AdminWorkKind.OWNERSHIP_CHANGE, row.value1(),
                        row.value2() + " · " + row.value3() + " → " + row.value4(), row.value5().toInstant()));
    }
}
