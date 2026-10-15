package com.ticketon.application.organizer.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerName;

/**
 * 운영진에서 내보내짐 메일 — 내보내진 그 사람의 운영진 로그인 이메일로 보낸다(docs/12 "주최자 대상 — 운영진에서 내보내짐 (그 사람에게)").
 * <p>
 * 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다). 받는 범위가 그 사람이라 주최자 id가 아니라 그 사람의 이메일을 직접
 * 연락처로 넘긴다 — 이미 운영진에서 빠져 주최자로 펼치면 닿지 않는다. 지난 소속은 이력으로 남고 그 주최자의 정산·현황은 볼 수 없다
 * (docs/01 "운영진 프로필 — 지난 소속").
 */
final class RemovedFromStaffNotice {

    private RemovedFromStaffNotice() {
    }

    static SendNotificationCommand commandFor(OrganizerName organizerName, EmailAddress removedStaffEmail) {
        return new SendNotificationCommand(NotificationRecipient.contact(ContactPoint.emailOnly(removedStaffEmail)),
                NotificationKind.REMOVED_FROM_STAFF, new NotificationContent(titleOf(organizerName), bodyOf(organizerName)));
    }

    static String titleOf(OrganizerName organizerName) {
        return "'%s' 운영진에서 내보내졌습니다".formatted(organizerName.value());
    }

    private static String bodyOf(OrganizerName organizerName) {
        return """
                '%s'의 소유자가 운영진에서 내보냈습니다.
                이제 이 주최자의 주최 관리에 들어갈 수 없습니다.
                함께한 기간은 운영진 프로필의 참여한 주최자에 남습니다.
                """.formatted(organizerName.value());
    }
}
