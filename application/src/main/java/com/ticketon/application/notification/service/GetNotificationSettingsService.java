package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.in.GetNotificationSettingsQuery;
import com.ticketon.application.notification.port.in.GetNotificationSettingsUseCase;
import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import com.ticketon.application.notification.port.out.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 설정 조회 — 저장된 선택에 기본값을 채운 설정을 화면 순서대로 돌려준다.
 */
@Service
public class GetNotificationSettingsService implements GetNotificationSettingsUseCase {

    private final NotificationSettingsRepository settingsRepository;

    public GetNotificationSettingsService(NotificationSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationSettingsResult getSettings(GetNotificationSettingsQuery query) {
        return NotificationSettingsResult.from(settingsRepository.findByAudienceAccountId(query.account().id()));
    }
}
