package com.ticketon.api.notification;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.notification.port.in.NotificationInboxResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 알림함 응답 — 마이페이지 알림함(docs/12 "전달 수단과 알림함", docs/13). 보낸 알림을 최신순으로 담는다.
 * 안 읽은 개수는 담지 않는다 — 탭에 숫자 배지를 달지 않는다(docs/99 D "알림함 읽음 표시").
 *
 * @param notifications 보낸 알림, 최신순
 */
@Schema(description = "내 알림함")
public record NotificationInboxResponse(
        @Schema(description = "보낸 알림, 최신순. 없으면 빈 목록", requiredMode = REQUIRED)
        List<NotificationInboxItem> notifications) {

    static NotificationInboxResponse from(NotificationInboxResult result) {
        return new NotificationInboxResponse(result.notifications().stream()
                .map(item -> new NotificationInboxItem(item.id(), item.title(), item.body(),
                        item.sentAt().atZone(KoreanTime.ZONE).toOffsetDateTime(), item.isNew()))
                .toList());
    }

    /**
     * @param id     알림함 항목 id
     * @param title  보낸 제목
     * @param body   보낸 본문
     * @param sentAt 보낸 시각 — 한국 시각
     * @param isNew  새로 온 것인지
     */
    @Schema(description = "알림함 한 줄")
    public record NotificationInboxItem(
            @Schema(description = "알림함 항목 id", example = "42", requiredMode = REQUIRED)
            long id,

            @Schema(description = "보낸 제목", example = "회차가 취소되었습니다", requiredMode = REQUIRED)
            String title,

            @Schema(description = "보낸 본문 — 이메일·문자로 보낸 내용 그대로", requiredMode = REQUIRED)
            String body,

            @Schema(description = "보낸 시각 — 한국 시각(+09:00)", example = "2026-10-15T12:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime sentAt,

            @Schema(description = "새로 온 것인지 — 지난번에 알림함을 연 뒤 온 것이라 굵게(점 표시) 보인다. 이 요청으로 읽음이 되어 "
                    + "다음 조회에서는 false다", requiredMode = REQUIRED)
            boolean isNew) {
    }
}
