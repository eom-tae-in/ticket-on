package com.ticketon.api.notification;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.account.port.in.ChangeMarketingConsentUseCase;
import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceUseCase;
import com.ticketon.application.notification.port.in.GetNotificationSettingsQuery;
import com.ticketon.application.notification.port.in.GetNotificationSettingsUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 알림 설정 — 관객 영역(/api/audience)이라 관객으로 로그인해야 열린다(api/security). 계정 단위 설정이다(docs/12 "알림 설정").
 */
@RestController
@RequestMapping("/api/audience/notification-settings")
@Tag(name = "알림 설정", description = "마이페이지 알림 설정 · 마케팅 수신 (docs/12 \"알림 설정\", \"마케팅 수신은 구분해서 표시\")")
public class NotificationSettingsController {

    private final GetNotificationSettingsUseCase getNotificationSettingsUseCase;
    private final ChangeNotificationPreferenceUseCase changeNotificationPreferenceUseCase;
    private final ChangeMarketingConsentUseCase changeMarketingConsentUseCase;

    public NotificationSettingsController(GetNotificationSettingsUseCase getNotificationSettingsUseCase,
            ChangeNotificationPreferenceUseCase changeNotificationPreferenceUseCase,
            ChangeMarketingConsentUseCase changeMarketingConsentUseCase) {
        this.getNotificationSettingsUseCase = getNotificationSettingsUseCase;
        this.changeNotificationPreferenceUseCase = changeNotificationPreferenceUseCase;
        this.changeMarketingConsentUseCase = changeMarketingConsentUseCase;
    }

    @Operation(summary = "내 알림 설정 조회", description = "끌 수 있는 세 묶음과 받는지를 화면 순서대로 돌려준다. 바꾼 적 없는 묶음은 기본값(지금은 받음)이다. "
            + "끌 수 없는 알림(행사 변경·취소·연기·장소·시간·출연자 변경·공연 중단)은 목록에 없다. "
            + "마케팅 수신은 묶음과 따로 동의 여부와 동의 일시를 담는다.")
    @GetMapping
    public NotificationSettingsResponse getSettings(@CurrentAccount AccountRef account) {
        return NotificationSettingsResponse.from(
                getNotificationSettingsUseCase.getSettings(new GetNotificationSettingsQuery(account)));
    }

    @Operation(summary = "알림 설정 한 줄 바꾸기", description = "주소의 묶음을 켜거나 끄고, 바뀐 뒤의 설정 전체를 돌려준다. 이용 정지 중에도 바꿀 수 있다. "
            + "모르는 묶음 이름이면 400이다.")
    @PutMapping("/{preference}")
    public NotificationSettingsResponse changePreference(@CurrentAccount AccountRef account,
            @PathVariable NotificationPreferenceCode preference,
            @Valid @RequestBody ChangeNotificationPreferenceRequest request) {
        return NotificationSettingsResponse.from(
                changeNotificationPreferenceUseCase.changePreference(request.toCommand(account, preference)));
    }

    @Operation(summary = "마케팅 수신 동의·철회", description = "마케팅 수신(신규 행사 소식 이메일)에 동의하거나 철회하고, 바뀐 뒤의 상태를 돌려준다. "
            + "바뀌면 동의 기록에 새 일시로 한 줄이 더해지고(이전 기록은 그대로) 처리 결과 안내 메일이 바로 간다. 지금 상태와 같으면 아무것도 "
            + "바꾸지 않고 안내도 보내지 않는다. 이용 정지 중에도 바꿀 수 있다. 동의 여부가 빠지면 400이다.")
    @PutMapping("/marketing")
    public MarketingConsentResponse changeMarketingConsent(@CurrentAccount AccountRef account,
            @Valid @RequestBody ChangeMarketingConsentRequest request) {
        return MarketingConsentResponse.from(changeMarketingConsentUseCase.changeConsent(request.toCommand(account)));
    }
}
