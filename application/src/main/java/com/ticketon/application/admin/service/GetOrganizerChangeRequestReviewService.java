package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewQuery;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewUseCase;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult.BusinessInformation;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult.SettlementAccount;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult.StaffMember;
import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 화면 조회 — 관리자 권한(종류의 관리자 일)을 확인하고, 요청과 그 주최자의 지금 값 · 명단을 읽어 확인 화면에
 * 쓸 값으로 옮긴다. 승인할 수 있는지의 마지막 판정은 승인할 때 도메인({@link OrganizerChangeRequest#approve})이 한다 — 여기서는 화면이 보여 줄
 * 사실만 담는다.
 */
@Service
public class GetOrganizerChangeRequestReviewService implements GetOrganizerChangeRequestReviewUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OrganizerChangeRequestRepository changeRequestRepository;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final StaffAccountRepository staffAccountRepository;

    public GetOrganizerChangeRequestReviewService(AdminAccountRepository adminAccountRepository,
            OrganizerChangeRequestRepository changeRequestRepository, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, StaffAccountRepository staffAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.staffAccountRepository = staffAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerChangeRequestReviewResult getReview(GetOrganizerChangeRequestReviewQuery query) {
        long requestId = query.changeRequestId();
        OrganizerChangeRequest request = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND,
                        "변경 요청 " + requestId));
        ReviewingAdmin.of(adminAccountRepository, query.admin()).requirePermission(request.kind().adminAction());
        long organizerId = request.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("변경 요청이 가리키는 주최자가 없습니다: " + organizerId));
        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        OrganizerBusinessDetails current = organizerRepository.findBusinessDetails(organizerId)
                .orElseThrow(() -> new IllegalStateException("변경 요청을 낸 주최자에 사업자 정보 · 정산 계좌가 없습니다: " + organizerId));
        boolean requesterIsOwner = staff.currentOwner().stream()
                .anyMatch(owner -> owner == request.requesterStaffAccountId());
        StaffMember currentOwner = staff.currentOwner().isPresent() ? staffMember(staff.currentOwner().getAsLong()) : null;
        Values values = valuesOf(request.requestedChange(), current);
        return new OrganizerChangeRequestReviewResult(
                requestId,
                organizerId,
                organizer.name().value(),
                request.kind(),
                request.status(),
                request.requestedAt(),
                request.reviewedAt().orElse(null),
                request.rejectionReason().map(RejectionReason::text).orElse(null),
                staffMember(request.requesterStaffAccountId()),
                requesterIsOwner,
                currentOwner,
                values.currentSettlementAccount(),
                values.requestedSettlementAccount(),
                values.currentBusinessInformation(),
                values.requestedBusinessInformation());
    }

    private StaffMember staffMember(long staffAccountId) {
        String nickname = staffAccountRepository.findById(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("변경 요청 · 소속이 가리키는 운영진 계정이 없습니다: " + staffAccountId))
                .nickname()
                .value();
        return new StaffMember(staffAccountId, nickname);
    }

    /** 종류에 맞는 지금 값 · 요청한 값 — 다른 종류의 칸은 비워 둔다. */
    private static Values valuesOf(RequestedOrganizerChange change, OrganizerBusinessDetails current) {
        return switch (change) {
            case RequestedOrganizerChange.NewSettlementAccount(var requested) -> new Values(
                    SettlementAccount.from(current.settlementAccount()), SettlementAccount.from(requested), null, null);
            case RequestedOrganizerChange.NewBusinessInformation(var requested) -> new Values(null, null,
                    current.businessInformation().map(BusinessInformation::from).orElse(null),
                    BusinessInformation.from(requested));
        };
    }

    private record Values(SettlementAccount currentSettlementAccount, SettlementAccount requestedSettlementAccount,
            BusinessInformation currentBusinessInformation, BusinessInformation requestedBusinessInformation) {
    }
}
