package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestUseCase;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerChangeRequestRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 계좌 · 사업자 정보 변경 반려 — 반려 규칙(확인 중일 때만, 사유 필수)은 {@link OrganizerChangeRequest#reject}가 보고, 여기서는 관리자
 * 권한(종류의 관리자 일)을 확인하고 결과를 저장하고, 사유와 함께 조작 기록을 남기고, 사유를 담은 결과 메일을 소유자에게 보낸다.
 * <p>
 * 주최자 값은 건드리지 않는다 — 운영진 계정 줄 · 주최자 줄을 잠그지 않고 요청 줄만 고친다. 한 트랜잭션이고, 요청은 표에서 아직 확인 중일 때만
 * 바뀐다 — 다른 관리자가 거의 동시에 먼저 처리했으면 충돌로 멈추고 기록도 메일도 남지 않는다. 요청한 사람이 그 사이 소유자를 넘겼어도 반려는
 * 한다 — 메일은 그때의 소유자에게 간다(docs/12 "정산 계좌 변경 확인 결과 (소유자에게)").
 */
@Service
public class RejectOrganizerChangeRequestService implements RejectOrganizerChangeRequestUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OrganizerChangeRequestRepository changeRequestRepository;
    private final OrganizerRepository organizerRepository;
    private final AdminOperationRecorder operationRecorder;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public RejectOrganizerChangeRequestService(AdminAccountRepository adminAccountRepository,
            OrganizerChangeRequestRepository changeRequestRepository, OrganizerRepository organizerRepository,
            AdminOperationRecorder operationRecorder, SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.organizerRepository = organizerRepository;
        this.operationRecorder = operationRecorder;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void reject(RejectOrganizerChangeRequestCommand command) {
        Instant now = clock.instant();
        AdminAccount admin = ReviewingAdmin.of(adminAccountRepository, command.admin());
        long requestId = command.changeRequestId();
        OrganizerChangeRequest request = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND,
                        "변경 요청 " + requestId));
        admin.requirePermission(request.kind().adminAction());

        request.reject(command.reason(), command.admin().id(), now);

        changeRequestRepository.saveReview(request);
        long organizerId = request.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("변경 요청이 가리키는 주최자가 없습니다: " + organizerId));
        operationRecorder.record(AdminOperationRecord.organizerChangeRequestRejected(admin.accountRef(), requestId,
                request.kind(), organizer.name(), command.reason(), now));
        sendNotificationUseCase.send(OrganizerChangeDecidedNotice.rejected(organizerId, organizer.name(), request.kind(),
                command.reason()));
    }
}
