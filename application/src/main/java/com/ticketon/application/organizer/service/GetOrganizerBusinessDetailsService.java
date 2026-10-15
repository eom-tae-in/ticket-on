package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsUseCase;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult.BusinessInformation;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult.MaskedSettlementAccount;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult.PendingBusinessInformationChange;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult.PendingSettlementAccountChange;
import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.StaffAction;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자의 사업자 정보 · 정산 계좌 보기 — 소유자 권한을 확인하고({@link StaffAction#MANAGE_SETTLEMENT_ACCOUNT}, 소유자만), 지금 값과 확인 중인
 * 변경 요청을 읽어 화면에 쓸 값으로 옮긴다. 정산 계좌번호는 가린다. 읽기만 하고 잠그지 않는다.
 */
@Service
public class GetOrganizerBusinessDetailsService implements GetOrganizerBusinessDetailsUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerChangeRequestRepository changeRequestRepository;

    public GetOrganizerBusinessDetailsService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            OrganizerRepository organizerRepository, OrganizerChangeRequestRepository changeRequestRepository) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.organizerRepository = organizerRepository;
        this.changeRequestRepository = changeRequestRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerBusinessDetailsResult get(GetOrganizerBusinessDetailsQuery query) {
        long organizerId = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.MANAGE_SETTLEMENT_ACCOUNT)).organizerId();
        OrganizerBusinessDetails current = organizerRepository.findBusinessDetails(organizerId)
                .orElseThrow(() -> new IllegalStateException("활동 중인 주최자에 사업자 정보 · 정산 계좌가 없습니다: " + organizerId));
        List<OrganizerChangeRequest> underReview = changeRequestRepository.findUnderReviewOf(organizerId);
        return new OrganizerBusinessDetailsResult(
                organizerId,
                current.businessStatus(),
                current.businessInformation().map(BusinessInformation::from).orElse(null),
                MaskedSettlementAccount.from(current.settlementAccount()),
                pendingSettlementAccountChangeOf(underReview),
                pendingBusinessInformationChangeOf(underReview));
    }

    private static PendingSettlementAccountChange pendingSettlementAccountChangeOf(List<OrganizerChangeRequest> underReview) {
        for (OrganizerChangeRequest request : underReview) {
            if (request.requestedChange() instanceof RequestedOrganizerChange.NewSettlementAccount(var account)) {
                return new PendingSettlementAccountChange(request.id().orElseThrow(), request.requestedAt(),
                        MaskedSettlementAccount.from(account));
            }
        }
        return null;
    }

    private static PendingBusinessInformationChange pendingBusinessInformationChangeOf(
            List<OrganizerChangeRequest> underReview) {
        for (OrganizerChangeRequest request : underReview) {
            if (request.requestedChange() instanceof RequestedOrganizerChange.NewBusinessInformation(var information)) {
                return new PendingBusinessInformationChange(request.id().orElseThrow(), request.requestedAt(),
                        BusinessInformation.from(information));
            }
        }
        return null;
    }
}
