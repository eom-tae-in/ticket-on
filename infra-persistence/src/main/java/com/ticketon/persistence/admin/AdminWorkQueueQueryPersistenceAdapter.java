package com.ticketon.persistence.admin;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_NAME_CHANGE;
import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CHANGE;
import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CLAIM;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;

import com.ticketon.application.admin.port.out.AdminWorkQueueQueryPort;
import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.application.admin.query.PendingAdminWork;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OwnershipChangeMethod;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
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
 *   <li>소유자 변경 — 소유자 변경 이력 표(ownership_change — V13)의 확인 전인 소유자 넘기기 줄. 소유자 이전 신청을 승인해 바꾼 줄(V14
 *       method = CLAIM_APPROVAL)은 관리자가 확인한 뒤 바꾼 것이라 담지 않는다. 제목은 "주최자명 · 넘긴 사람 → 받은 사람"이고 사람은
 *       공개 프로필의 닉네임으로 적는다(탈퇴해도 닉네임은 남는다). 접수 일시는 넘긴 일시다.</li>
 *   <li>소유자 이전 신청 — 소유자 이전 신청 표(ownership_claim — V14)의 심사 중인 줄. 제목은 "주최자명 · 신청한 사람"이고 사람은 공개
 *       프로필의 닉네임으로 적는다. 접수 일시는 신청 일시다. 사유는 심사 화면에서 본다.</li>
 *   <li>주최자명 변경 — 주최자명 변경 이력 표(organizer_name_change — V17)의 확인 전인 줄. 제목은 "바꾸기 전 이름 → 바꾼 이름"으로 그 한
 *       건의 두 이름을 그대로 적는다 — 그 뒤 또 바꿨어도 줄마다 무엇을 무엇으로 바꿨는지 읽힌다. 접수 일시는 바꾼 일시다.</li>
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
        return Stream.of(pendingOrganizerApplications(), unconfirmedOwnershipChanges(), pendingOwnershipClaims(),
                        unconfirmedOrganizerNameChanges())
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

    private List<PendingAdminWork> pendingOwnershipClaims() {
        return dsl.select(OWNERSHIP_CLAIM.ID, ORGANIZER.NAME, STAFF_ACCOUNT.NICKNAME, OWNERSHIP_CLAIM.SUBMITTED_AT)
                .from(OWNERSHIP_CLAIM)
                .join(ORGANIZER).on(ORGANIZER.ID.eq(OWNERSHIP_CLAIM.ORGANIZER_ID))
                .join(STAFF_ACCOUNT).on(STAFF_ACCOUNT.ID.eq(OWNERSHIP_CLAIM.CLAIMANT_STAFF_ACCOUNT_ID))
                .where(OWNERSHIP_CLAIM.STATUS.eq(OwnershipClaimStatus.UNDER_REVIEW.name()))
                .fetch(row -> new PendingAdminWork(AdminWorkKind.OWNERSHIP_CLAIM, row.value1(),
                        row.value2() + " · " + row.value3(), row.value4().toInstant()));
    }

    private List<PendingAdminWork> unconfirmedOrganizerNameChanges() {
        return dsl.select(ORGANIZER_NAME_CHANGE.ID, ORGANIZER_NAME_CHANGE.PREVIOUS_NAME, ORGANIZER_NAME_CHANGE.NEW_NAME,
                        ORGANIZER_NAME_CHANGE.CHANGED_AT)
                .from(ORGANIZER_NAME_CHANGE)
                .where(ORGANIZER_NAME_CHANGE.CONFIRMED_AT.isNull())
                .fetch(row -> new PendingAdminWork(AdminWorkKind.ORGANIZER_NAME_CHANGE, row.value1(),
                        row.value2() + " → " + row.value3(), row.value4().toInstant()));
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
                .and(OWNERSHIP_CHANGE.METHOD.eq(OwnershipChangeMethod.OWNER_HANDOVER.name()))
                .fetch(row -> new PendingAdminWork(AdminWorkKind.OWNERSHIP_CHANGE, row.value1(),
                        row.value2() + " · " + row.value3() + " → " + row.value4(), row.value5().toInstant()));
    }
}
