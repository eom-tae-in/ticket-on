package com.ticketon.application.organizer.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerNameChange;

/**
 * 주최자명 변경 메일 — 그 주최자의 운영진 전원에게 보낸다(docs/12 "주최자 대상 — 주최자명 변경 (운영진 전원에게)", docs/01 "공개 페이지 —
 * 주최자명 변경: 운영진 전원과 관리자에게 알림").
 * <p>
 * 주최자 id로 보내므로 바꾼 소유자를 포함한 운영진 전원에게 간다({@link NotificationKind#ORGANIZER_NAME_CHANGED}). 주최자 쪽 알림이라 이메일로만
 * 가고 제목에 [주최]가 붙는다(알림이 붙인다). 바꾼 사람은 공개 프로필의 닉네임으로 적는다 — 소유자 모르게 바뀐 일이면 운영진이 바로 알아채게.
 * 관리자에게는 메일 대신 처리 목록으로 간다(docs/12 "관리자 대상").
 */
final class OrganizerNameChangedNotice {

    private OrganizerNameChangedNotice() {
    }

    static SendNotificationCommand commandFor(long organizerId, OrganizerNameChange change, Nickname changedBy) {
        return new SendNotificationCommand(NotificationRecipient.organizer(organizerId),
                NotificationKind.ORGANIZER_NAME_CHANGED, new NotificationContent(titleOf(change), bodyOf(change, changedBy)));
    }

    static String titleOf(OrganizerNameChange change) {
        return "주최자명이 바뀌었습니다: %s → %s".formatted(change.previousName().value(), change.newName().value());
    }

    private static String bodyOf(OrganizerNameChange change, Nickname changedBy) {
        return """
                주최자명이 바뀌었습니다.
                바꾸기 전: %s
                바꾼 뒤: %s
                바꾼 사람: %s 님(소유자)
                주최자 페이지와 지난 행사 페이지에도 바꾼 이름이 보입니다. 모르는 변경이라면 문의해 주세요.
                """.formatted(change.previousName().value(), change.newName().value(), changedBy.value());
    }
}
