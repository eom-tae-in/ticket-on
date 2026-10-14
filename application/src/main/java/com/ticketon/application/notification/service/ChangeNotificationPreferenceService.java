package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceCommand;
import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceUseCase;
import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import com.ticketon.application.notification.port.out.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정 한 줄 바꾸기 — 그 묶음의 선택만 저장하고, 저장한 뒤의 설정을 다시 읽어 돌려준다.
 * <p>
 * 묶음 하나만 저장하므로, 관객이 두 묶음을 거의 동시에 바꿔도 한쪽이 다른 쪽을 옛 값으로 덮지 않는다
 * ({@link NotificationSettingsRepository#saveChoice}).
 */
@Service
public class ChangeNotificationPreferenceService implements ChangeNotificationPreferenceUseCase {

    private final NotificationSettingsRepository settingsRepository;

    public ChangeNotificationPreferenceService(NotificationSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    @Transactional
    public NotificationSettingsResult changePreference(ChangeNotificationPreferenceCommand command) {
        long audienceAccountId = command.account().id();
        settingsRepository.saveChoice(audienceAccountId, command.preference(), command.receives());
        return NotificationSettingsResult.from(settingsRepository.findByAudienceAccountId(audienceAccountId));
    }
}
