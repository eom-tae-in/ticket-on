package com.ticketon.application.organizer.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import java.util.ArrayList;
import java.util.List;

/** 보내 달라고 한 알림을 모아 두는 알림 발송 유스케이스. 수단·이력·[주최] 접두사는 알림 유스케이스의 테스트가 본다. */
final class RecordingSendNotificationUseCase implements SendNotificationUseCase {

    private final List<SendNotificationCommand> sentCommands = new ArrayList<>();

    List<SendNotificationCommand> sentCommands() {
        return List.copyOf(sentCommands);
    }

    @Override
    public void send(SendNotificationCommand command) {
        sentCommands.add(command);
    }
}
