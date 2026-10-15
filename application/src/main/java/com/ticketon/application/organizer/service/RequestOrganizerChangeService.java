package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeResult;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 — 요청 규칙(종류마다 확인 중인 요청 하나, 지금 값과 같은 값은 받지 않음, 사업자 정보는 사업자만)은
 * {@link OrganizerChangeRequest#request}가 보고, 여기서는 소유자 권한을 확인하고 주최자를 잠가 지금 값을 읽어 넘긴 뒤 요청을 저장하고 조작
 * 기록을 남긴다. 주최자의 값은 건드리지 않는다 — 관리자가 승인할 때 적용한다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 요청하는 사람의 계정 줄 잡기 → 주최자 잠가 읽기 → 권한 다시 확인 → 지금 값 · 확인 중인 요청 읽기 → 요청
 * → 저장 → 조작 기록 순서(주최자 줄을 잠그는 흐름의 공통 시작 — {@link LockedOrganizer}, 잠금 순서 {@link StaffAccountLocks}).
 * <ul>
 *   <li><b>잠근 뒤 권한을 다시 확인한다.</b> 계정 줄에서 기다리는 사이 소유자를 넘겼으면 이제 운영자라
 *       {@link com.ticketon.domain.organizer.OrganizerErrorCode#OWNER_ONLY_ACTION}으로 막는다 — 넘긴 사람의 계좌 변경 요청이 새 소유자 모르게
 *       남지 않는다.</li>
 *   <li>주최자 줄을 쥔 동안에는 관리자의 변경 승인(주최자 줄을 먼저 잠근다)도 기다리므로, 읽은 지금 값과 확인 중인 요청이 저장할 때까지
 *       그대로다. 같은 소유자가 거의 동시에 두 번 요청하면 뒤의 요청은 주최자 줄에서 기다렸다가 확인 중인 요청을 보고 막힌다(409).</li>
 * </ul>
 * 조작 기록 요약은 "정산 계좌 변경 요청" · "사업자 정보 변경 요청"이다 — 계좌번호 · 사업자등록번호 같은 값은 싣지 않는다. 관리자에게는 알림 대신
 * 처리 목록으로 가므로 메일은 없다(docs/12 "관리자 대상").
 */
@Service
public class RequestOrganizerChangeService implements RequestOrganizerChangeUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerChangeRequestRepository changeRequestRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final Clock clock;

    public RequestOrganizerChangeService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            OrganizerChangeRequestRepository changeRequestRepository, StaffAccountRepository staffAccountRepository,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RequestOrganizerChangeResult request(RequestOrganizerChangeCommand command) {
        Instant now = clock.instant();
        OrganizerChangeRequestKind kind = command.requestedChange().kind();
        LockedOrganizer locked = LockedOrganizer.lock(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(command.actingStaff(), kind.staffAction()));
        StaffPermissionResult permission = locked.permission();
        long organizerId = permission.organizerId();
        OrganizerBusinessDetails current = organizerRepository.findBusinessDetails(organizerId)
                .orElseThrow(() -> new IllegalStateException("활동 중인 주최자에 사업자 정보 · 정산 계좌가 없습니다: " + organizerId));

        OrganizerChangeRequest request = OrganizerChangeRequest.request(organizerId, permission.staffAccountId(), current,
                command.requestedChange(), changeRequestRepository.hasUnderReview(organizerId, kind), now);

        OrganizerChangeRequest saved = changeRequestRepository.add(request);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.ORGANIZER,
                organizerId, operationSummaryOf(kind), now));
        return new RequestOrganizerChangeResult(saved.id().orElseThrow(), kind, saved.requestedAt());
    }

    /** 조작 기록의 요약 — "정산 계좌 변경 요청" · "사업자 정보 변경 요청". 값은 싣지 않는다. */
    static String operationSummaryOf(OrganizerChangeRequestKind kind) {
        return kind.label() + " 변경 요청";
    }
}
