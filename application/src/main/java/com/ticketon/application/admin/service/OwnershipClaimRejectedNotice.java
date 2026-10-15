package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.RejectionReason;

/**
 * 소유자 이전 신청 반려 메일 — 사유를 담아 그 주최자의 운영진 전원(신청한 운영자 포함)에게 보낸다({@link NotificationKind#OWNERSHIP_CLAIM_REJECTED},
 * #27 "결과를 신청자와 운영진 전원에게", docs/12 "주최자 대상 — 받는 사람"). 소유자는 그대로다. 사람은 공개 프로필의 닉네임으로 적는다.
 * 주최자 쪽 알림이라 제목에 [주최]가 붙는다(알림이 붙인다).
 */
final class OwnershipClaimRejectedNotice {

    private OwnershipClaimRejectedNotice() {
    }

    static SendNotificationCommand commandFor(long organizerId, OrganizerName organizerName, Nickname claimant,
            RejectionReason reason) {
        return new SendNotificationCommand(NotificationRecipient.organizer(organizerId),
                NotificationKind.OWNERSHIP_CLAIM_REJECTED,
                new NotificationContent(titleOf(organizerName), bodyOf(organizerName, claimant, reason)));
    }

    static String titleOf(OrganizerName organizerName) {
        return "'%s' 소유자 이전 신청이 반려되었습니다".formatted(organizerName.value());
    }

    private static String bodyOf(OrganizerName organizerName, Nickname claimant, RejectionReason reason) {
        return """
                '%s'의 소유자 이전 신청이 반려되었습니다. 소유자는 바뀌지 않았습니다.
                신청한 분: %s 님
                반려 사유: %s
                """.formatted(organizerName.value(), claimant.value(), reason.text());
    }
}
