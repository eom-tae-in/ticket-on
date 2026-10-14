package com.ticketon.api.notification;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.notification.port.in.NotificationSettingsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 내 알림 설정 응답 — 마이페이지 알림 설정(docs/12 "알림 설정"). 끌 수 있는 묶음만 화면 순서대로 담는다.
 * 끌 수 없는 알림(행사 변경·취소·연기·장소·시간·출연자 변경·공연 중단)은 목록에 없다.
 *
 * @param preferences 묶음과 받는지, 화면 순서대로
 */
@Schema(description = "내 알림 설정")
public record NotificationSettingsResponse(
        @Schema(description = "끌 수 있는 알림 묶음과 받는지. 화면 순서대로 — 예매·취소 확인, 공연 전날 리마인더, 예매 오픈 알림",
                requiredMode = REQUIRED)
        List<NotificationPreferenceItem> preferences) {

    static NotificationSettingsResponse from(NotificationSettingsResult result) {
        return new NotificationSettingsResponse(result.preferences().stream()
                .map(choice -> new NotificationPreferenceItem(NotificationPreferenceCode.from(choice.preference()),
                        choice.receives()))
                .toList());
    }

    /**
     * @param preference 묶음
     * @param receives   받는지
     */
    @Schema(description = "알림 설정 한 줄")
    public record NotificationPreferenceItem(
            @Schema(description = "묶음 — BOOKING_CONFIRMATION(예매·취소 확인), DAY_BEFORE_REMINDER(공연 전날 리마인더), "
                    + "BOOKING_OPEN(예매 오픈 알림)", requiredMode = REQUIRED)
            NotificationPreferenceCode preference,

            @Schema(description = "받는지. 바꾼 적 없으면 받음이다", requiredMode = REQUIRED)
            boolean receives) {
    }
}
