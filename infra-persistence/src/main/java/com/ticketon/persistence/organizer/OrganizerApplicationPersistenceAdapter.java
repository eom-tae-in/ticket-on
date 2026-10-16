package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION_LINK;

import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.ApplicantReviewState;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import com.ticketon.persistence.jooq.tables.records.OrganizerApplicationRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Component;

/**
 * 주최자 신청서 저장소(organizer_application · organizer_application_link — V8) 어댑터.
 *
 * <h2>심사 중인 신청은 한 사람에게 하나 — 동시 신청 경합</h2>
 * 신청은 "심사 중인 신청이 있는지 확인 → 저장" 두 단계라, 같은 사람이 거의 동시에 두 번 내면 둘 다 확인을 통과할 수 있다. 그래서 마지막 판정은
 * 표의 유일 색인(심사 중인 신청은 신청한 사람마다 하나)이 한다. 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지
 * 않으면 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 들어온 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 결과를 기다렸다가
 * 판정하므로 둘 다 들어가는 일은 없다. 진 쪽의 트랜잭션은 되돌려져, 함께 만든 심사 중 주최자도 남지 않는다.
 *
 * <h2>같은 신청을 두 관리자가 동시에 처리 — 심사 경합</h2>
 * 승인·반려는 "신청서를 읽어 심사 중인지 확인 → 저장" 두 단계라, 두 관리자가 거의 동시에 처리하면 둘 다 확인을 통과할 수 있다. 그래서 저장은
 * {@code UPDATE … WHERE status = 'UNDER_REVIEW'}로 하고, 바뀐 줄이 없으면 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 바꾼
 * 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 줄의 잠금을 기다렸다가 바뀐 상태로 조건을 다시 보므로 둘 다 바뀌는 일은 없다.
 *
 * <h2>민감한 값</h2>
 * 담당자 연락처 · 사업자 정보 · 정산 계좌는 오류 설명에도 넣지 않는다. 신청서는 id와 신청한 운영진 id로만 가리킨다.
 */
@Component
public class OrganizerApplicationPersistenceAdapter implements OrganizerApplicationRepository {

    private final DSLContext dsl;

    public OrganizerApplicationPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ApplicantReviewState reviewStateOf(long applicantStaffAccountId) {
        boolean underReview = dsl.fetchExists(ORGANIZER_APPLICATION,
                ORGANIZER_APPLICATION.APPLICANT_STAFF_ACCOUNT_ID.eq(applicantStaffAccountId)
                        .and(ORGANIZER_APPLICATION.STATUS.eq(OrganizerApplicationStatus.UNDER_REVIEW.name())));
        return underReview ? ApplicantReviewState.APPLICATION_UNDER_REVIEW : ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW;
    }

    @Override
    public OrganizerApplication add(OrganizerApplication newApplication) {
        if (newApplication.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 신청서입니다: " + newApplication.id().getAsLong());
        }
        long id = insertApplication(newApplication).orElseThrow(() -> new ConflictException(
                OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW,
                "동시 신청 경합 — 운영진 " + newApplication.applicantStaffAccountId() + "의 다른 신청이 먼저 저장됨"));
        insertLinks(id, newApplication.form().websiteAndSnsLinks());
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 신청서를 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<OrganizerApplication> findById(long id) {
        return dsl.selectFrom(ORGANIZER_APPLICATION)
                .where(ORGANIZER_APPLICATION.ID.eq(id))
                .fetchOptional()
                .map(application -> toApplication(application, linksOf(id)));
    }

    @Override
    public Optional<OrganizerApplication> findLatestApprovedOf(long organizerId) {
        return dsl.select(ORGANIZER_APPLICATION.ID)
                .from(ORGANIZER_APPLICATION)
                .where(ORGANIZER_APPLICATION.ORGANIZER_ID.eq(organizerId))
                .and(ORGANIZER_APPLICATION.STATUS.eq(OrganizerApplicationStatus.APPROVED.name()))
                .orderBy(ORGANIZER_APPLICATION.REVIEWED_AT.desc(), ORGANIZER_APPLICATION.ID.desc())
                .limit(1)
                .fetchOptional(Record1::value1)
                .flatMap(this::findById);
    }

    @Override
    public void saveReview(OrganizerApplication reviewedApplication) {
        long id = reviewedApplication.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 신청서는 심사 결과를 저장할 수 없습니다"));
        if (reviewedApplication.status() == OrganizerApplicationStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("아직 심사 중인 신청서입니다: " + id);
        }
        int updated = dsl.update(ORGANIZER_APPLICATION)
                .set(ORGANIZER_APPLICATION.STATUS, reviewedApplication.status().name())
                .set(ORGANIZER_APPLICATION.REVIEWED_AT,
                        reviewedApplication.reviewedAt().map(this::toDatabaseTime).orElseThrow())
                .set(ORGANIZER_APPLICATION.REJECTION_REASON,
                        reviewedApplication.rejectionReason().map(RejectionReason::text).orElse(null))
                .where(ORGANIZER_APPLICATION.ID.eq(id))
                .and(ORGANIZER_APPLICATION.STATUS.eq(OrganizerApplicationStatus.UNDER_REVIEW.name()))
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED,
                    "심사 경합 — 신청서 " + id + "를 다른 처리가 먼저 마침");
        }
    }

    private Optional<Long> insertApplication(OrganizerApplication application) {
        OrganizerApplicationForm form = application.form();
        Optional<BusinessInformation> business = Optional.ofNullable(form.businessDeclaration().businessInformation());
        SettlementAccount settlementAccount = form.settlementAccount();
        return dsl.insertInto(ORGANIZER_APPLICATION)
                .set(ORGANIZER_APPLICATION.ORGANIZER_ID, application.organizerId())
                .set(ORGANIZER_APPLICATION.APPLICANT_STAFF_ACCOUNT_ID, application.applicantStaffAccountId())
                .set(ORGANIZER_APPLICATION.STATUS, application.status().name())
                .set(ORGANIZER_APPLICATION.ORGANIZER_TYPE, form.organizerType().name())
                .set(ORGANIZER_APPLICATION.NAME, form.name().value())
                .set(ORGANIZER_APPLICATION.CONTACT_PHONE_NUMBER, form.contactPhoneNumber().value())
                .set(ORGANIZER_APPLICATION.EMAIL, form.email().value())
                .set(ORGANIZER_APPLICATION.EVENT_HISTORY_OR_PLAN, form.eventHistoryOrPlan())
                .set(ORGANIZER_APPLICATION.BUSINESS_STATUS, form.businessStatus().name())
                .set(ORGANIZER_APPLICATION.BUSINESS_TRADE_NAME, business.map(BusinessInformation::tradeName).orElse(null))
                .set(ORGANIZER_APPLICATION.BUSINESS_REPRESENTATIVE_NAME,
                        business.map(BusinessInformation::representativeName).orElse(null))
                .set(ORGANIZER_APPLICATION.BUSINESS_ADDRESS, business.map(BusinessInformation::businessAddress).orElse(null))
                .set(ORGANIZER_APPLICATION.BUSINESS_PHONE_NUMBER,
                        business.map(information -> information.businessPhoneNumber().value()).orElse(null))
                .set(ORGANIZER_APPLICATION.BUSINESS_REGISTRATION_NUMBER,
                        business.map(information -> information.registrationNumber().digits()).orElse(null))
                .set(ORGANIZER_APPLICATION.SETTLEMENT_BANK_NAME, settlementAccount.bankName())
                .set(ORGANIZER_APPLICATION.SETTLEMENT_ACCOUNT_NUMBER, settlementAccount.accountNumber())
                .set(ORGANIZER_APPLICATION.SETTLEMENT_ACCOUNT_HOLDER, settlementAccount.accountHolder())
                .set(ORGANIZER_APPLICATION.SUBMITTED_AT, toDatabaseTime(application.submittedAt()))
                .set(ORGANIZER_APPLICATION.REVIEWED_AT, application.reviewedAt().map(this::toDatabaseTime).orElse(null))
                .set(ORGANIZER_APPLICATION.REJECTION_REASON,
                        application.rejectionReason().map(RejectionReason::text).orElse(null))
                .onConflictDoNothing()
                .returningResult(ORGANIZER_APPLICATION.ID)
                .fetchOptional()
                .map(Record1::value1);
    }

    private void insertLinks(long applicationId, List<ExternalLink> links) {
        for (int displayOrder = 0; displayOrder < links.size(); displayOrder++) {
            dsl.insertInto(ORGANIZER_APPLICATION_LINK)
                    .set(ORGANIZER_APPLICATION_LINK.ORGANIZER_APPLICATION_ID, applicationId)
                    .set(ORGANIZER_APPLICATION_LINK.DISPLAY_ORDER, displayOrder)
                    .set(ORGANIZER_APPLICATION_LINK.URL, links.get(displayOrder).value())
                    .execute();
        }
    }

    private List<ExternalLink> linksOf(long applicationId) {
        return dsl.select(ORGANIZER_APPLICATION_LINK.URL)
                .from(ORGANIZER_APPLICATION_LINK)
                .where(ORGANIZER_APPLICATION_LINK.ORGANIZER_APPLICATION_ID.eq(applicationId))
                .orderBy(ORGANIZER_APPLICATION_LINK.DISPLAY_ORDER)
                .fetch(Record1::value1)
                .stream()
                .map(ExternalLink::new)
                .toList();
    }

    private OrganizerApplication toApplication(OrganizerApplicationRecord application, List<ExternalLink> links) {
        OrganizerApplicationForm form = new OrganizerApplicationForm(
                OrganizerType.valueOf(application.getOrganizerType()),
                new OrganizerName(application.getName()),
                new TelephoneNumber(application.getContactPhoneNumber()),
                new EmailAddress(application.getEmail()),
                links,
                application.getEventHistoryOrPlan(),
                toBusinessDeclaration(application),
                BusinessColumns.settlementAccountOf(application.getSettlementBankName(),
                        application.getSettlementAccountNumber(), application.getSettlementAccountHolder()));
        Instant reviewedAt = application.getReviewedAt() == null ? null : application.getReviewedAt().toInstant();
        RejectionReason rejectionReason = application.getRejectionReason() == null ? null
                : new RejectionReason(application.getRejectionReason());
        return OrganizerApplication.restore(application.getId(), application.getOrganizerId(),
                application.getApplicantStaffAccountId(), form, application.getSubmittedAt().toInstant(),
                OrganizerApplicationStatus.valueOf(application.getStatus()), reviewedAt, rejectionReason);
    }

    private static BusinessDeclaration toBusinessDeclaration(OrganizerApplicationRecord application) {
        return BusinessColumns.declarationOf(application.getBusinessStatus(), application.getBusinessTradeName(),
                application.getBusinessRepresentativeName(), application.getBusinessAddress(),
                application.getBusinessPhoneNumber(), application.getBusinessRegistrationNumber());
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
