package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.in.NotificationInboxResult;
import com.ticketon.application.notification.port.in.OpenNotificationInboxCommand;
import com.ticketon.application.notification.port.in.OpenNotificationInboxUseCase;
import com.ticketon.application.notification.port.out.NotificationInboxRepository;
import com.ticketon.domain.notification.InboxNotification;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 열기 — 최신순 목록을 열기 전 상태 그대로 돌려주고, 새로 온 것들은 지금을 처음 본 시각으로 저장한다
 * ({@link InboxNotification#markSeen}). 다음에 열면 그것들은 새로 온 것이 아니다.
 */
@Service
public class OpenNotificationInboxService implements OpenNotificationInboxUseCase {

    private final NotificationInboxRepository inboxRepository;
    private final Clock clock;

    public OpenNotificationInboxService(NotificationInboxRepository inboxRepository, Clock clock) {
        this.inboxRepository = inboxRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NotificationInboxResult open(OpenNotificationInboxCommand command) {
        Instant now = clock.instant();
        List<InboxNotification> inbox = inboxRepository.findByAudienceAccountId(command.account().id());
        NotificationInboxResult result = NotificationInboxResult.from(inbox);
        List<InboxNotification> newlyArrived = inbox.stream()
                .filter(InboxNotification::isNew)
                .toList();
        newlyArrived.forEach(notification -> notification.markSeen(now));
        inboxRepository.saveFirstSeen(newlyArrived);
        return result;
    }
}
