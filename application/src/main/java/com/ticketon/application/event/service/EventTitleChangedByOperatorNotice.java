package com.ticketon.application.event.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.event.EventChangeAuthor;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * '운영자가 행사 제목을 바꿈' 메일 — 그 주최자의 소유자에게만 보낸다(docs/12 "주최자 대상 — 운영자가 행사 제목을 바꿈 (소유자에게)",
 * {@link NotificationKind#EVENT_TITLE_CHANGED_BY_OPERATOR}). 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다).
 * <p>
 * <b>승인이 아니라 알림이다</b> — 소유자 모르게 바뀌는 일을 막되 승인으로 막지는 않는다(docs/03 "제목은 운영진 누구나, 몇 번이든 고칠 수 있습니다",
 * docs/99 D "운영자의 제목 변경"). 그래서 바꾸기 전 · 뒤 제목과 바꾼 운영자를 적어 소유자가 바로 알아보게 하고, 할 일이 없다는 것을 함께 적는다. 한
 * 소유자가 여러 주최자를 가질 수 있어 주최자명을 적는다. 바꾼 사람은 공개 프로필의 닉네임으로 적는다.
 */
final class EventTitleChangedByOperatorNotice {

    private EventTitleChangedByOperatorNotice() {
    }

    /**
     * @param titleChange 운영자가 바꾼 제목 버전 — 바꾸기 전이 있다
     * @param operator    바꾼 운영자
     * @param changedAt   바꾼 순간
     */
    static SendNotificationCommand commandFor(long organizerId, String organizerName, EventChangeHistoryVersion titleChange,
            EventChangeAuthor operator, Instant changedAt) {
        return new SendNotificationCommand(NotificationRecipient.organizer(organizerId), NotificationKind.EVENT_TITLE_CHANGED_BY_OPERATOR,
                new NotificationContent(titleOf(titleChange), bodyOf(organizerName, titleChange, operator, changedAt)));
    }

    static String titleOf(EventChangeHistoryVersion titleChange) {
        return "운영자가 행사 제목을 바꿨습니다: %s → %s".formatted(titleChange.previousContent(), titleChange.newContent());
    }

    private static String bodyOf(String organizerName, EventChangeHistoryVersion titleChange, EventChangeAuthor operator,
            Instant changedAt) {
        LocalDateTime changedAtInKorea = KoreanTime.toDateTime(changedAt);
        return """
                '%s'의 행사 제목을 운영자가 바꿨습니다.
                바꾸기 전: %s
                바꾼 뒤: %s
                바꾼 사람: %s 님(운영자)
                바꾼 때: %d년 %d월 %d일 %02d:%02d
                승인할 일은 없으며 알려 드리기만 합니다. 모르는 변경이라면 바꾼 운영자와 확인해 주세요.
                바꾸기 전 제목은 변경 이력에 남습니다.
                """.formatted(organizerName, titleChange.previousContent(), titleChange.newContent(), operator.nickname().value(),
                changedAtInKorea.getYear(), changedAtInKorea.getMonthValue(), changedAtInKorea.getDayOfMonth(),
                changedAtInKorea.getHour(), changedAtInKorea.getMinute());
    }
}
