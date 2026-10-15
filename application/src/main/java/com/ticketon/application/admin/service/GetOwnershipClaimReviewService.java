package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewQuery;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewUseCase;
import com.ticketon.application.admin.port.in.OwnershipClaimReviewResult;
import com.ticketon.application.organizer.port.out.OrganizerApplicationRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.StaffRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 이전 신청 심사 화면 조회 — 관리자 권한(소유자 이전)을 확인하고, 신청과 그 주최자의 지금 명단 · 승인된 신청서를 읽어 심사 화면에 쓸 값으로
 * 옮긴다. 승인할 수 있는지의 마지막 판정은 승인할 때 도메인({@link OwnershipClaim#approve})이 한다 — 여기서는 화면이 보여 줄 사실만 담는다.
 */
@Service
public class GetOwnershipClaimReviewService implements GetOwnershipClaimReviewUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OwnershipClaimRepository claimRepository;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerApplicationRepository applicationRepository;

    public GetOwnershipClaimReviewService(AdminAccountRepository adminAccountRepository,
            OwnershipClaimRepository claimRepository, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, StaffAccountRepository staffAccountRepository,
            OrganizerApplicationRepository applicationRepository) {
        this.adminAccountRepository = adminAccountRepository;
        this.claimRepository = claimRepository;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnershipClaimReviewResult getReview(GetOwnershipClaimReviewQuery query) {
        ReviewingAdmin.of(adminAccountRepository, query.admin())
                .requirePermission(AdminAction.TRANSFER_ORGANIZER_OWNERSHIP);
        OwnershipClaim claim = claimRepository.findById(query.ownershipClaimId())
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND,
                        "소유자 이전 신청 " + query.ownershipClaimId()));
        long organizerId = claim.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("소유자 이전 신청이 가리키는 주최자가 없습니다: " + organizerId));
        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        boolean claimantIsOperator = staff.roleOf(claim.claimantStaffAccountId())
                .filter(role -> role == StaffRole.OPERATOR)
                .isPresent();
        OwnershipClaimReviewResult.StaffMember currentOwner = staff.currentOwner().isPresent()
                ? staffMember(staff.currentOwner().getAsLong()) : null;
        return new OwnershipClaimReviewResult(
                claim.id().orElseThrow(),
                organizerId,
                organizer.name().value(),
                claim.status(),
                claim.reason().text(),
                claim.submittedAt(),
                claim.reviewedAt().orElse(null),
                claim.rejectionReason().map(RejectionReason::text).orElse(null),
                staffMember(claim.claimantStaffAccountId()),
                claimantIsOperator,
                staffMember(claim.ownerStaffAccountId()),
                currentOwner,
                applicationRepository.findLatestApprovedOf(organizerId)
                        .map(GetOwnershipClaimReviewService::contactOf)
                        .orElse(null));
    }

    private OwnershipClaimReviewResult.StaffMember staffMember(long staffAccountId) {
        String nickname = staffAccountRepository.findById(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("신청 · 소속이 가리키는 운영진 계정이 없습니다: " + staffAccountId))
                .nickname()
                .value();
        return new OwnershipClaimReviewResult.StaffMember(staffAccountId, nickname);
    }

    private static OwnershipClaimReviewResult.ApplicationContact contactOf(OrganizerApplication application) {
        OrganizerApplicationForm form = application.form();
        return new OwnershipClaimReviewResult.ApplicationContact(application.id().orElseThrow(),
                form.contactPhoneNumber().value(), form.email().value(),
                form.websiteAndSnsLinks().stream().map(ExternalLink::value).toList());
    }
}
