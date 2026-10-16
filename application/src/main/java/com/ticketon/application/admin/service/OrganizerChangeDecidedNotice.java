package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.RejectionReason;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 결과 메일 — 승인 / 반려(사유 포함)를 <b>소유자에게</b> 보낸다(docs/12 "주최자 대상 — 정산 계좌 변경 확인
 * 결과 (소유자에게)", "정산 계좌처럼 소유자만 다루는 일은 소유자에게만"). 사업자 정보 쪽은 표 밖 종류다
 * ({@link NotificationKind#BUSINESS_INFORMATION_CHANGE_DECIDED}).
 * <p>
 * 주최자 id로 보내 그때의 소유자에게 간다. 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다). 계좌번호 · 사업자등록번호
 * 같은 값은 메일에 싣지 않는다 — 소유자는 주최 관리 화면에서 본다.
 */
final class OrganizerChangeDecidedNotice {

    private OrganizerChangeDecidedNotice() {
    }

    static SendNotificationCommand approved(long organizerId, OrganizerName organizerName, OrganizerChangeRequestKind kind) {
        return new SendNotificationCommand(NotificationRecipient.organizer(organizerId), notificationKindOf(kind),
                new NotificationContent(approvedTitleOf(organizerName, kind), """
                        '%s'의 %s 변경 요청을 관리자가 확인해 승인했습니다.
                        %s
                        """.formatted(organizerName.value(), kind.label(), appliedSentenceOf(kind))));
    }

    static SendNotificationCommand rejected(long organizerId, OrganizerName organizerName, OrganizerChangeRequestKind kind,
            RejectionReason reason) {
        return new SendNotificationCommand(NotificationRecipient.organizer(organizerId), notificationKindOf(kind),
                new NotificationContent(rejectedTitleOf(organizerName, kind), """
                        '%s'의 %s 변경 요청이 반려되었습니다. %s
                        반려 사유: %s
                        """.formatted(organizerName.value(), kind.label(), keptSentenceOf(kind), reason.text())));
    }

    static String approvedTitleOf(OrganizerName organizerName, OrganizerChangeRequestKind kind) {
        return "'%s' %s 변경이 승인되었습니다".formatted(organizerName.value(), kind.label());
    }

    static String rejectedTitleOf(OrganizerName organizerName, OrganizerChangeRequestKind kind) {
        return "'%s' %s 변경이 반려되었습니다".formatted(organizerName.value(), kind.label());
    }

    private static NotificationKind notificationKindOf(OrganizerChangeRequestKind kind) {
        return switch (kind) {
            case SETTLEMENT_ACCOUNT -> NotificationKind.SETTLEMENT_ACCOUNT_CHANGE_DECIDED;
            case BUSINESS_INFORMATION -> NotificationKind.BUSINESS_INFORMATION_CHANGE_DECIDED;
        };
    }

    private static String appliedSentenceOf(OrganizerChangeRequestKind kind) {
        return switch (kind) {
            case SETTLEMENT_ACCOUNT -> "이제부터 바꾼 정산 계좌로 정산됩니다.";
            case BUSINESS_INFORMATION -> "이제부터 행사 페이지 \"판매자 정보\"에 바꾼 사업자 정보가 보입니다.";
        };
    }

    private static String keptSentenceOf(OrganizerChangeRequestKind kind) {
        return switch (kind) {
            case SETTLEMENT_ACCOUNT -> "지금 정산 계좌가 그대로 쓰입니다.";
            case BUSINESS_INFORMATION -> "지금 사업자 정보가 그대로 판매자 정보에 보입니다.";
        };
    }
}
