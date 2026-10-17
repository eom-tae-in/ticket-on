package com.ticketon.application.event.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.domain.notification.NotificationRecipient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 보내 달라고 한 알림을 모아 두는 알림 발송 유스케이스. 수단·이력·[주최] 접두사는 알림 유스케이스의 테스트가 본다. 실패하게 둔 주최자에게 보내면
 * 발송이 실패한 것처럼 예외를 던진다.
 */
final class RecordingSendNotificationUseCase implements SendNotificationUseCase {

    private final List<SendNotificationCommand> sentCommands = new ArrayList<>();
    private final Set<Long> failingOrganizers = new HashSet<>();

    List<SendNotificationCommand> sentCommands() {
        return List.copyOf(sentCommands);
    }

    /** 그 주최자에게 보내면 실패한다. */
    void failFor(long organizerId) {
        failingOrganizers.add(organizerId);
    }

    @Override
    public void send(SendNotificationCommand command) {
        if (command.recipient() instanceof NotificationRecipient.Organizer organizer
                && failingOrganizers.contains(organizer.organizerId())) {
            throw new IllegalStateException("메일을 보내지 못했습니다: 주최자 " + organizer.organizerId());
        }
        sentCommands.add(command);
    }
}
