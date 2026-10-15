package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerApplicationForm;

/**
 * 주최자 신청 승인 메일 — 신청서의 통지 이메일로 보낸다(docs/01 "신청 항목: 이메일 — 통지", docs/12 "주최자 대상 — 승인 / 반려").
 * <p>
 * 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다). 받는 범위가 그 사람(INDIVIDUAL)이라 주최자 id가 아니라 신청서의
 * 이메일을 직접 연락처로 넘긴다 — 반려 메일과 같은 곳으로 보낸다.
 */
final class OrganizerApplicationApprovedNotice {

    static final String TITLE = "주최자 신청이 승인되었습니다";

    private OrganizerApplicationApprovedNotice() {
    }

    static SendNotificationCommand commandFor(OrganizerApplicationForm form) {
        return new SendNotificationCommand(NotificationRecipient.contact(ContactPoint.emailOnly(form.email())),
                NotificationKind.ORGANIZER_APPLICATION_APPROVED, new NotificationContent(TITLE, bodyOf(form)));
    }

    private static String bodyOf(OrganizerApplicationForm form) {
        return """
                '%s' 주최자 신청이 승인되었습니다.
                신청하신 분이 이 주최자의 소유자가 되었고, 이제 행사를 열 수 있습니다.
                운영진 로그인으로 주최 관리에 들어가 시작해 보세요.
                """.formatted(form.name().value());
    }
}
