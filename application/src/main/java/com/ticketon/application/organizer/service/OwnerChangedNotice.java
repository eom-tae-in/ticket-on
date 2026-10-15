package com.ticketon.application.organizer.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerName;

/**
 * 소유자 변경 메일 — 그 주최자의 운영진 전원에게 보낸다(docs/12 "주최자 대상 — 소유자 변경 (운영자 전원에게)", docs/01 "소유자 넘기기 —
 * 알림: 운영자 전원과 관리자에게").
 * <p>
 * 넘기기를 저장한 뒤 주최자 id로 보내므로 넘긴 뒤의 운영진 전원 — 받은 새 소유자와, 운영자로 남은 넘긴 사람을 포함한 운영자 모두 — 에게
 * 간다({@link NotificationKind#OWNER_CHANGED}). 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다). 사람은 공개
 * 프로필의 닉네임으로 적는다. 관리자에게는 메일 대신 처리 목록으로 간다(docs/12 "관리자 대상").
 */
final class OwnerChangedNotice {

    private OwnerChangedNotice() {
    }

    static SendNotificationCommand commandFor(long organizerId, OrganizerName organizerName, Nickname previousOwner,
            Nickname newOwner) {
        return new SendNotificationCommand(NotificationRecipient.organizer(organizerId), NotificationKind.OWNER_CHANGED,
                new NotificationContent(titleOf(organizerName), bodyOf(organizerName, previousOwner, newOwner)));
    }

    static String titleOf(OrganizerName organizerName) {
        return "'%s' 소유자가 바뀌었습니다".formatted(organizerName.value());
    }

    private static String bodyOf(OrganizerName organizerName, Nickname previousOwner, Nickname newOwner) {
        return """
                '%s'의 소유자가 바뀌었습니다.
                %s 님이 %s 님에게 소유자를 넘겼고, %s 님은 운영자로 남습니다.
                소유자는 정산 계좌와 운영진 관리를 맡습니다. 모르는 변경이라면 문의해 주세요.
                """.formatted(organizerName.value(), previousOwner.value(), newOwner.value(), previousOwner.value());
    }
}
