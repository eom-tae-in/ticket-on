package com.ticketon.application.notification.service;

import com.ticketon.application.account.port.in.GetMarketingConsentQuery;
import com.ticketon.application.account.port.in.GetMarketingConsentUseCase;
import com.ticketon.application.notification.port.in.GetNotificationSettingsQuery;
import com.ticketon.application.notification.port.in.GetNotificationSettingsUseCase;
import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import com.ticketon.application.notification.port.out.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 알림 설정 조회 — 저장된 선택에 기본값을 채운 설정을 화면 순서대로 돌려주고, 마케팅 수신 상태는 계정의 동의 기록에서 읽어 함께 담는다
 * ({@link GetMarketingConsentUseCase}).
 */
@Service
public class GetNotificationSettingsService implements GetNotificationSettingsUseCase {

    private final NotificationSettingsRepository settingsRepository;
    private final GetMarketingConsentUseCase getMarketingConsentUseCase;

    public GetNotificationSettingsService(NotificationSettingsRepository settingsRepository,
            GetMarketingConsentUseCase getMarketingConsentUseCase) {
        this.settingsRepository = settingsRepository;
        this.getMarketingConsentUseCase = getMarketingConsentUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationSettingsResult getSettings(GetNotificationSettingsQuery query) {
        return NotificationSettingsResult.from(settingsRepository.findByAudienceAccountId(query.account().id()),
                getMarketingConsentUseCase.getConsent(new GetMarketingConsentQuery(query.account())));
    }
}
