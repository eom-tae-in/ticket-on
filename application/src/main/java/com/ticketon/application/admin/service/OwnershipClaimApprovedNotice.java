package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerName;

/**
 * 소유자 이전 신청 승인 메일 — 소유자가 바뀌는 일이라 표의 '소유자 변경'({@link NotificationKind#OWNER_CHANGED})으로 그 주최자의 운영진
 * 전원에게 보낸다(docs/12 "주최자 대상 — 소유자 변경 (운영자 전원에게)", docs/01 "소유자가 바뀌면 운영자 전원과 관리자에게 알립니다").
 * <p>
 * 역할 교대를 저장한 뒤 주최자 id로 보내므로 바뀐 뒤의 운영진 전원 — 새 소유자가 된 신청한 운영자와, 운영자로 남은 신청 때의 소유자를 포함 —
 * 에게 간다. 신청한 사람에게 가는 결과 알림도 이것이다. 관리자는 직접 바꿨으므로 처리 목록에 따로 쌓지 않는다. 사람은 공개 프로필의 닉네임으로
 * 적는다. 주최자 쪽 알림이라 제목에 [주최]가 붙는다(알림이 붙인다).
 */
final class OwnershipClaimApprovedNotice {

    private OwnershipClaimApprovedNotice() {
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
                '%s'의 소유자 이전 신청이 승인되어 소유자가 바뀌었습니다.
                %s 님이 새 소유자가 되었고, %s 님은 운영자로 남습니다.
                소유자와 연락이 끊긴 경우 운영자의 신청을 받아 관리자가 확인한 뒤 바꿉니다. 모르는 변경이라면 문의해 주세요.
                """.formatted(organizerName.value(), newOwner.value(), previousOwner.value());
    }
}
