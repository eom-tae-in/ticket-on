package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.RejectionReason;
import java.util.Objects;

/**
 * 주최자 신청 반려 메일 — 사유를 담아 신청서의 통지 이메일로 보낸다(docs/01 "반려 — 사유를 반드시 남기고 전달합니다", docs/12 "주최자 대상").
 * <p>
 * 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다). 반려할 때는 그 주최자에 소유자도 운영자도 없어 주최자 id로 펼칠 수
 * 없으므로 신청서의 이메일을 직접 연락처로 넘긴다(INDIVIDUAL). 재신청은 제한이 없다고 함께 알린다(docs/01 "반려").
 */
final class OrganizerApplicationRejectedNotice {

    static final String TITLE = "주최자 신청이 반려되었습니다";

    private OrganizerApplicationRejectedNotice() {
    }

    static SendNotificationCommand commandFor(OrganizerApplicationForm form, RejectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        return new SendNotificationCommand(NotificationRecipient.contact(ContactPoint.emailOnly(form.email())),
                NotificationKind.ORGANIZER_APPLICATION_REJECTED, new NotificationContent(TITLE, bodyOf(form, reason)));
    }

    private static String bodyOf(OrganizerApplicationForm form, RejectionReason reason) {
        return """
                '%s' 주최자 신청이 반려되었습니다.

                반려 사유: %s

                내용을 보완해 언제든 다시 신청할 수 있습니다.
                """.formatted(form.name().value(), reason.text());
    }
}
