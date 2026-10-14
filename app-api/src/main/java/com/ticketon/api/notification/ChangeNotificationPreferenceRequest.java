package com.ticketon.api.notification;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.notification.port.in.ChangeNotificationPreferenceCommand;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 알림 설정 한 줄 바꾸기 요청. 바꿀 묶음은 주소에 담는다.
 * <p>
 * 받을지는 꼭 보낸다. 빠졌을 때 '안 받음'으로 읽으면 관객 모르게 알림이 꺼진다.
 */
@Schema(description = "알림 설정 한 줄 바꾸기 요청")
public record ChangeNotificationPreferenceRequest(
        @Schema(description = "받을지", requiredMode = REQUIRED)
        @NotNull(message = "받을지 골라 주세요.")
        Boolean receives) {

    ChangeNotificationPreferenceCommand toCommand(AccountRef account, NotificationPreferenceCode preference) {
        return new ChangeNotificationPreferenceCommand(account, preference.toPreference(), receives);
    }
}
