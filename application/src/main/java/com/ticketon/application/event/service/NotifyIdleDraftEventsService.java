package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.NotifyIdleDraftEventsResult;
import com.ticketon.application.event.port.in.NotifyIdleDraftEventsUseCase;
import com.ticketon.application.event.port.out.DraftEventIdleNoticeRepository;
import com.ticketon.domain.event.DraftEventNudges;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 작성 중 행사 30일 미수정 알림 — 알릴 때가 됐을 수 있는 행사를 찾아({@link DraftEventNudges#idleNoticeCutoff}) 행사마다 따로 알린다
 * ({@link IdleDraftEventNotifier}).
 * <p>
 * <b>트랜잭션은 행사마다 따로다</b> — 이 서비스는 트랜잭션을 열지 않는다. 한 트랜잭션으로 묶으면 한 행사의 실패가 모든 행사의 알림을 되돌려,
 * 같은 실패가 되풀이되는 동안 아무도 알림을 받지 못한다. 행사마다 나누면 실패한 행사만 기록이 남지 않아 다음 작업이 다시 시도하고, 행사 줄도
 * 그 행사의 알림을 남기는 동안만 잡는다.
 * <p>
 * 실패를 삼키지 않는다 — 나머지 행사를 모두 처리한 뒤 실패한 행사 id와 원인(첫 원인은 cause, 나머지는 suppressed)을 담아 던진다. 정해진 시각
 * 작업의 오류 기록에 남는다.
 */
@Service
public class NotifyIdleDraftEventsService implements NotifyIdleDraftEventsUseCase {

    private final DraftEventIdleNoticeRepository noticeRepository;
    private final IdleDraftEventNotifier notifier;
    private final Clock clock;

    public NotifyIdleDraftEventsService(DraftEventIdleNoticeRepository noticeRepository, IdleDraftEventNotifier notifier,
            Clock clock) {
        this.noticeRepository = noticeRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public NotifyIdleDraftEventsResult notifyIdleDraftEvents() {
        Instant now = clock.instant();
        List<Long> notified = new ArrayList<>();
        Map<Long, RuntimeException> failures = new LinkedHashMap<>();
        for (long eventId : noticeRepository.findNoticeCandidates(DraftEventNudges.idleNoticeCutoff(now))) {
            try {
                if (notifier.notifyIfIdle(eventId, now)) {
                    notified.add(eventId);
                }
            } catch (RuntimeException failure) {
                failures.put(eventId, failure);
            }
        }
        if (!failures.isEmpty()) {
            throw failureOf(failures, notified);
        }
        return new NotifyIdleDraftEventsResult(notified);
    }

    private static IllegalStateException failureOf(Map<Long, RuntimeException> failures, List<Long> notified) {
        List<RuntimeException> causes = List.copyOf(failures.values());
        IllegalStateException failure = new IllegalStateException("작성 중 행사 30일 미수정 알림을 보내지 못한 행사가 있습니다: 행사 "
                + failures.keySet() + " (보낸 행사 " + notified + ")", causes.getFirst());
        causes.stream().skip(1).forEach(failure::addSuppressed);
        return failure;
    }
}
