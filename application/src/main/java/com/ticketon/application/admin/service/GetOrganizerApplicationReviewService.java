package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewQuery;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewUseCase;
import com.ticketon.application.admin.port.in.OrganizerApplicationReviewResult;
import com.ticketon.application.admin.port.out.OrganizerApplicationReviewQueryPort;
import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerReviewChecklistItem;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RejectionReasonTemplate;
import com.ticketon.domain.organizer.SettlementAccount;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 심사 화면 조회 — 관리자 권한(심사)을 확인하고, 신청서를 읽어 심사 화면에 쓸 값으로 옮기고, 이력 세탁 대조(같은 소유자 또는 같은
 * 단체명의 닫힌 주최자)를 붙인다. 심사 목표일은 도메인({@link OrganizerApplication#reviewDeadline})이 정한다.
 */
@Service
public class GetOrganizerApplicationReviewService implements GetOrganizerApplicationReviewUseCase {

    private static final List<OrganizerReviewChecklistItem> CHECKLIST = List.of(OrganizerReviewChecklistItem.values());

    private static final List<String> REJECTION_REASON_TEMPLATES = Arrays.stream(RejectionReasonTemplate.values())
            .map(RejectionReasonTemplate::reason)
            .map(RejectionReason::text)
            .toList();

    private final AdminAccountRepository adminAccountRepository;
    private final OrganizerApplicationRepository applicationRepository;
    private final OrganizerApplicationReviewQueryPort reviewQueryPort;
    private final BusinessDayCalculator businessDayCalculator;

    public GetOrganizerApplicationReviewService(AdminAccountRepository adminAccountRepository,
            OrganizerApplicationRepository applicationRepository, OrganizerApplicationReviewQueryPort reviewQueryPort,
            BusinessDayCalculator businessDayCalculator) {
        this.adminAccountRepository = adminAccountRepository;
        this.applicationRepository = applicationRepository;
        this.reviewQueryPort = reviewQueryPort;
        this.businessDayCalculator = businessDayCalculator;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerApplicationReviewResult getReview(GetOrganizerApplicationReviewQuery query) {
        ReviewingAdmin.of(adminAccountRepository, query.reviewer())
                .requirePermission(AdminAction.REVIEW_ORGANIZER_APPLICATION);
        OrganizerApplication application = applicationRepository.findById(query.applicationId())
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.APPLICATION_NOT_FOUND,
                        "신청서 " + query.applicationId()));
        OrganizerApplicationForm form = application.form();
        return new OrganizerApplicationReviewResult(
                application.id().orElseThrow(),
                application.organizerId(),
                application.applicantStaffAccountId(),
                application.status(),
                application.submittedAt(),
                application.reviewDeadline(businessDayCalculator).dueDate(),
                application.reviewedAt().orElse(null),
                application.rejectionReason().map(RejectionReason::text).orElse(null),
                formOf(form),
                CHECKLIST,
                REJECTION_REASON_TEMPLATES,
                reviewQueryPort.similarClosedOrganizers(application.applicantStaffAccountId(), form.name()));
    }

    private static OrganizerApplicationReviewResult.Form formOf(OrganizerApplicationForm form) {
        return new OrganizerApplicationReviewResult.Form(
                form.organizerType(),
                form.name().value(),
                form.contactPhoneNumber().value(),
                form.email().value(),
                form.websiteAndSnsLinks().stream().map(ExternalLink::value).toList(),
                form.eventHistoryOrPlan(),
                form.businessStatus(),
                businessInformationOf(form.businessDeclaration().businessInformation()),
                settlementAccountOf(form.settlementAccount()));
    }

    private static OrganizerApplicationReviewResult.BusinessInformation businessInformationOf(
            BusinessInformation information) {
        if (information == null) {
            return null;
        }
        return new OrganizerApplicationReviewResult.BusinessInformation(information.tradeName(),
                information.representativeName(), information.businessAddress(),
                information.businessPhoneNumber().value(), information.registrationNumber().digits());
    }

    private static OrganizerApplicationReviewResult.SettlementAccount settlementAccountOf(SettlementAccount account) {
        return new OrganizerApplicationReviewResult.SettlementAccount(account.bankName(), account.accountNumber(),
                account.accountHolder());
    }
}
