package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestUseCase;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 · 사업자 정보 변경 승인 — 승인 규칙(확인 중일 때만, 주최자 활동, 요청한 사람이 지금도 소유자, 그때의 값에 새 값 적용)은
 * {@link OrganizerChangeRequest#approve}가 보고, 여기서는 관리자 권한(종류의 관리자 일)을 확인하고 요청 · 주최자 · 명단 · 지금 값을 읽어 넘긴
 * 뒤 결과를 저장하고, 조작 기록을 남기고, 소유자에게 결과 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 관리자 권한 확인 → 요청한 사람의 계정 줄(FOR KEY SHARE) → 주최자 줄(FOR UPDATE) → 요청 다시 읽기 · 명단 · 지금 값
 * → 승인 → 요청 저장(조건부) → 주최자 값 저장 → 조작 기록 → 메일 순서(잠금 순서 {@link StaffAccountLocks}).
 * <ul>
 *   <li><b>요청한 사람의 계정 줄을 먼저 잡는다.</b> 소유자 넘기기 · 나가기 · 운영진 로그인 탈퇴는 그 계정 줄을 FOR UPDATE로 먼저 잠그므로, 그런
 *       흐름이 진행 중이면 끝날 때까지 기다렸다가 명단을 읽는다 — 소유자를 넘긴 직후에 넘긴 사람의 요청이 적용되지 않는다(요청한 사람이 지금 소유자가
 *       아니면 409 CHANGE_REQUESTER_NOT_OWNER). 잡은 동안에는 그 흐름들이 기다린다.</li>
 *   <li><b>주최자 줄을 요청 줄보다 먼저 잠근다.</b> 소유자의 새 요청은 주최자 줄을 쥔 채 같은 종류의 요청 줄(유일 색인)을 기다릴 수 있어, 요청 줄을
 *       먼저 고치고 주최자 줄을 기다리면 서로 맞물린다. 주최자 줄을 쥐므로 정산 계좌 승인과 사업자 정보 승인이 거의 동시여도 뒤의 승인은 앞의 승인이
 *       저장한 값을 읽고 적용한다 — 서로의 값을 덮지 않는다.</li>
 *   <li><b>두 관리자가 거의 동시에 처리하면</b> — 둘 다 승인이면 늦은 쪽은 주최자 줄에서 기다렸다가 다시 읽은 요청이 이미 승인이라
 *       {@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED}(409)로 멈춘다. 반려와 엇갈리면 요청의 조건부 저장이 하나만
 *       통과시킨다(409). 진 쪽은 주최자 값 · 기록 · 메일이 남지 않는다.</li>
 * </ul>
 * 계좌번호 · 사업자등록번호 같은 값은 조작 기록 요약과 메일에 싣지 않는다. 메일이 실패해도 승인은 그대로다.
 */
@Service
public class ApproveOrganizerChangeRequestService implements ApproveOrganizerChangeRequestUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OrganizerChangeRequestRepository changeRequestRepository;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AdminOperationRecorder operationRecorder;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public ApproveOrganizerChangeRequestService(AdminAccountRepository adminAccountRepository,
            OrganizerChangeRequestRepository changeRequestRepository, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, StaffAccountRepository staffAccountRepository,
            AdminOperationRecorder operationRecorder, SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.operationRecorder = operationRecorder;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void approve(ApproveOrganizerChangeRequestCommand command) {
        Instant now = clock.instant();
        AdminAccount admin = ReviewingAdmin.of(adminAccountRepository, command.admin());
        long requestId = command.changeRequestId();
        OrganizerChangeRequest requested = findRequest(requestId);
        admin.requirePermission(requested.kind().adminAction());
        long organizerId = requested.organizerId();
        StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, requested.requesterStaffAccountId());
        Organizer organizer = organizerRepository.findByIdForUpdate(organizerId)
                .orElseThrow(() -> new IllegalStateException("변경 요청이 가리키는 주최자가 없습니다: " + organizerId));

        OrganizerChangeRequest request = findRequest(requestId);
        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        OrganizerBusinessDetails current = organizerRepository.findBusinessDetails(organizerId)
                .orElseThrow(() -> new IllegalStateException("변경 요청을 낸 주최자에 사업자 정보 · 정산 계좌가 없습니다: " + organizerId));

        OrganizerBusinessDetails changed = request.approve(organizer, staff, current, command.admin().id(), now);

        changeRequestRepository.saveReview(request);
        organizerRepository.saveBusinessDetails(organizerId, changed);
        operationRecorder.record(AdminOperationRecord.organizerChangeRequestApproved(admin.accountRef(), requestId,
                request.kind(), organizer.name(), now));
        sendNotificationUseCase.send(OrganizerChangeDecidedNotice.approved(organizerId, organizer.name(), request.kind()));
    }

    private OrganizerChangeRequest findRequest(long requestId) {
        return changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND,
                        "변경 요청 " + requestId));
    }
}
