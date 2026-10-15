package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER_CHANGE_REQUEST;

import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.persistence.jooq.tables.records.OrganizerChangeRequestRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Record1;
import org.springframework.stereotype.Component;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 저장소(organizer_change_request — V18) 어댑터. 요청 한 번이 한 줄이고, 종류에 맞는 새 값 칸만 채운다
 * (정산 계좌면 세 칸, 사업자 정보면 다섯 칸).
 *
 * <h2>한 주최자에 같은 종류의 확인 중인 요청은 하나 — 동시 요청 경합</h2>
 * 요청은 "확인 중인 요청이 있는지 확인 → 저장" 두 단계라, 거의 동시에 두 번 내면 둘 다 확인을 통과할 수 있다. 그래서 마지막 판정은 표의 유일
 * 색인(organizer_change_request_under_review_ux)이 한다. 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지 않으면
 * 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 들어온 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 결과를 기다렸다가 판정한다.
 *
 * <h2>같은 요청을 두 관리자가 동시에 처리 — 확인 경합</h2>
 * 승인·반려는 {@code UPDATE … WHERE status = 'UNDER_REVIEW'}로 저장한다. 바뀐 줄이 없으면 진 쪽이라 {@link ConflictException}(409)이
 * 된다. 먼저 바꾼 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 줄의 잠금을 기다렸다가 바뀐 상태로 조건을 다시 본다.
 */
@Component
public class OrganizerChangeRequestPersistenceAdapter implements OrganizerChangeRequestRepository {

    private final DSLContext dsl;

    public OrganizerChangeRequestPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean hasUnderReview(long organizerId, OrganizerChangeRequestKind kind) {
        return dsl.fetchExists(ORGANIZER_CHANGE_REQUEST, ORGANIZER_CHANGE_REQUEST.ORGANIZER_ID.eq(organizerId)
                .and(ORGANIZER_CHANGE_REQUEST.KIND.eq(kind.name()))
                .and(ORGANIZER_CHANGE_REQUEST.STATUS.eq(OrganizerChangeRequestStatus.UNDER_REVIEW.name())));
    }

    @Override
    public OrganizerChangeRequest add(OrganizerChangeRequest newRequest) {
        if (newRequest.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 변경 요청입니다: " + newRequest.id().getAsLong());
        }
        InsertSetMoreStep<OrganizerChangeRequestRecord> insert = dsl.insertInto(ORGANIZER_CHANGE_REQUEST)
                .set(ORGANIZER_CHANGE_REQUEST.ORGANIZER_ID, newRequest.organizerId())
                .set(ORGANIZER_CHANGE_REQUEST.KIND, newRequest.kind().name())
                .set(ORGANIZER_CHANGE_REQUEST.REQUESTED_BY_STAFF_ACCOUNT_ID, newRequest.requesterStaffAccountId())
                .set(ORGANIZER_CHANGE_REQUEST.REQUESTED_AT, toDatabaseTime(newRequest.requestedAt()))
                .set(ORGANIZER_CHANGE_REQUEST.STATUS, newRequest.status().name());
        long id = withRequestedValue(insert, newRequest.requestedChange())
                .onConflictDoNothing()
                .returningResult(ORGANIZER_CHANGE_REQUEST.ID)
                .fetchOptional()
                .map(Record1::value1)
                .orElseThrow(() -> new ConflictException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW,
                        "동시 요청 경합 — 주최자 " + newRequest.organizerId() + "의 " + newRequest.kind()
                                + " 변경 요청이 먼저 저장됨"));
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 변경 요청을 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<OrganizerChangeRequest> findById(long id) {
        return dsl.selectFrom(ORGANIZER_CHANGE_REQUEST)
                .where(ORGANIZER_CHANGE_REQUEST.ID.eq(id))
                .fetchOptional()
                .map(OrganizerChangeRequestPersistenceAdapter::toRequest);
    }

    @Override
    public List<OrganizerChangeRequest> findUnderReviewOf(long organizerId) {
        return dsl.selectFrom(ORGANIZER_CHANGE_REQUEST)
                .where(ORGANIZER_CHANGE_REQUEST.ORGANIZER_ID.eq(organizerId))
                .and(ORGANIZER_CHANGE_REQUEST.STATUS.eq(OrganizerChangeRequestStatus.UNDER_REVIEW.name()))
                .orderBy(ORGANIZER_CHANGE_REQUEST.REQUESTED_AT, ORGANIZER_CHANGE_REQUEST.ID)
                .fetch(OrganizerChangeRequestPersistenceAdapter::toRequest);
    }

    @Override
    public void saveReview(OrganizerChangeRequest reviewedRequest) {
        long id = reviewedRequest.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 변경 요청은 처리 결과를 저장할 수 없습니다"));
        if (reviewedRequest.status() == OrganizerChangeRequestStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("아직 확인 중인 변경 요청입니다: " + id);
        }
        int updated = dsl.update(ORGANIZER_CHANGE_REQUEST)
                .set(ORGANIZER_CHANGE_REQUEST.STATUS, reviewedRequest.status().name())
                .set(ORGANIZER_CHANGE_REQUEST.REVIEWED_AT,
                        reviewedRequest.reviewedAt().map(OrganizerChangeRequestPersistenceAdapter::toDatabaseTime).orElseThrow())
                .set(ORGANIZER_CHANGE_REQUEST.REVIEWED_BY_ADMIN_ACCOUNT_ID,
                        reviewedRequest.reviewerAdminAccountId().orElseThrow())
                .set(ORGANIZER_CHANGE_REQUEST.REJECTION_REASON,
                        reviewedRequest.rejectionReason().map(RejectionReason::text).orElse(null))
                .where(ORGANIZER_CHANGE_REQUEST.ID.eq(id))
                .and(ORGANIZER_CHANGE_REQUEST.STATUS.eq(OrganizerChangeRequestStatus.UNDER_REVIEW.name()))
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED,
                    "확인 경합 — 변경 요청 " + id + "을 다른 처리가 먼저 마침");
        }
    }

    /** 종류에 맞는 새 값 칸만 채운다 — 나머지 칸은 비어 있다(표의 CHECK). */
    private static InsertSetMoreStep<OrganizerChangeRequestRecord> withRequestedValue(
            InsertSetMoreStep<OrganizerChangeRequestRecord> insert, RequestedOrganizerChange change) {
        return switch (change) {
            case RequestedOrganizerChange.NewSettlementAccount(SettlementAccount account) -> insert
                    .set(ORGANIZER_CHANGE_REQUEST.SETTLEMENT_BANK_NAME, account.bankName())
                    .set(ORGANIZER_CHANGE_REQUEST.SETTLEMENT_ACCOUNT_NUMBER, account.accountNumber())
                    .set(ORGANIZER_CHANGE_REQUEST.SETTLEMENT_ACCOUNT_HOLDER, account.accountHolder());
            case RequestedOrganizerChange.NewBusinessInformation(BusinessInformation information) -> insert
                    .set(ORGANIZER_CHANGE_REQUEST.BUSINESS_TRADE_NAME, information.tradeName())
                    .set(ORGANIZER_CHANGE_REQUEST.BUSINESS_REPRESENTATIVE_NAME, information.representativeName())
                    .set(ORGANIZER_CHANGE_REQUEST.BUSINESS_ADDRESS, information.businessAddress())
                    .set(ORGANIZER_CHANGE_REQUEST.BUSINESS_PHONE_NUMBER, information.businessPhoneNumber().value())
                    .set(ORGANIZER_CHANGE_REQUEST.BUSINESS_REGISTRATION_NUMBER, information.registrationNumber().digits());
        };
    }

    private static OrganizerChangeRequest toRequest(OrganizerChangeRequestRecord row) {
        Instant reviewedAt = row.getReviewedAt() == null ? null : row.getReviewedAt().toInstant();
        RejectionReason rejectionReason = row.getRejectionReason() == null ? null
                : new RejectionReason(row.getRejectionReason());
        return OrganizerChangeRequest.restore(row.getId(), row.getOrganizerId(), row.getRequestedByStaffAccountId(),
                requestedChangeOf(row), row.getRequestedAt().toInstant(), OrganizerChangeRequestStatus.valueOf(row.getStatus()),
                reviewedAt, row.getReviewedByAdminAccountId(), rejectionReason);
    }

    private static RequestedOrganizerChange requestedChangeOf(OrganizerChangeRequestRecord row) {
        return switch (OrganizerChangeRequestKind.valueOf(row.getKind())) {
            case SETTLEMENT_ACCOUNT -> new RequestedOrganizerChange.NewSettlementAccount(BusinessColumns.settlementAccountOf(
                    row.getSettlementBankName(), row.getSettlementAccountNumber(), row.getSettlementAccountHolder()));
            case BUSINESS_INFORMATION -> new RequestedOrganizerChange.NewBusinessInformation(BusinessColumns.informationOf(
                    row.getBusinessTradeName(), row.getBusinessRepresentativeName(), row.getBusinessAddress(),
                    row.getBusinessPhoneNumber(), row.getBusinessRegistrationNumber()));
        };
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
