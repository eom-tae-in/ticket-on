package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.DraftEventIdleNoticeRepository;
import com.ticketon.application.event.query.IdleDraftEvent;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.event.DraftEventNudges;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성 중 행사 하나에 30일 미수정 알림을 보낸다 — 행사 하나가 트랜잭션 하나다({@link NotifyIdleDraftEventsService}).
 * <ol>
 *   <li>행사 줄을 FOR KEY SHARE로 잡고 다시 읽는다({@link DraftEventIdleNoticeRepository#lockDraftEvent}) — 같은 행사의 위저드 저장이 진행 중이면
 *       끝날 때까지 기다렸다가 바뀐 값을 읽는다. 그사이 판매를 시작했거나 지웠거나 주최자가 닫혔으면 보내지 않는다.</li>
 *   <li>다시 읽은 마지막 수정 시각으로 30일이 지났는지 본다({@link DraftEventNudges#isIdleNoticeDue}) — 막 고쳤으면 보내지 않는다.</li>
 *   <li>그 마지막 수정 시각의 구간에 알렸다고 남긴다 — 이미 남아 있으면(다시 돌았거나 다른 작업이 먼저 남겼으면) 보내지 않는다.</li>
 *   <li>그 주최자의 운영진 전원에게 메일을 보낸다({@link DraftEventIdleNotice}) — 알림 발송은 같은 트랜잭션에서 발송 이력을 남기고, 실패하면
 *       기록과 함께 되돌려진다.</li>
 * </ol>
 * 잠금 순서: 행사 줄(③)만 잡는다 — 알림 기록의 외래 키 확인 잠금은 이미 쥔 그 행사 줄에 걸리고, 운영진 연락처는 잠그지 않고 읽는다(organizer
 * 서비스의 {@code StaffAccountLocks} 표).
 */
@Component
class IdleDraftEventNotifier {

    private final DraftEventIdleNoticeRepository noticeRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final WebAddress webAddress;

    IdleDraftEventNotifier(DraftEventIdleNoticeRepository noticeRepository, SendNotificationUseCase sendNotificationUseCase,
            WebAddress webAddress) {
        this.noticeRepository = noticeRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.webAddress = webAddress;
    }

    /**
     * @param now 이번 작업의 지금 — 판정과 알린 시각에 쓴다
     * @return 이번에 알렸는지
     */
    @Transactional
    public boolean notifyIfIdle(long eventId, Instant now) {
        Optional<IdleDraftEvent> locked = noticeRepository.lockDraftEvent(eventId)
                .filter(draft -> DraftEventNudges.isIdleNoticeDue(draft.lastModifiedAt(), now));
        if (locked.isEmpty() || !noticeRepository.recordNotice(eventId, locked.get().lastModifiedAt(), now)) {
            return false;
        }
        sendNotificationUseCase.send(DraftEventIdleNotice.commandFor(locked.get(), webAddress));
        return true;
    }
}
