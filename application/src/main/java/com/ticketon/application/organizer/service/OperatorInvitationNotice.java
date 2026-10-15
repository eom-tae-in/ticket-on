package com.ticketon.application.organizer.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.policy.ProposedPolicy;

/**
 * 운영자 초대 메일 — 초대받은 이메일로 보낸다(docs/12 "주최자 대상 — 운영자 초대 (받는 사람에게)").
 * <p>
 * 주최자 쪽 알림이라 이메일로만 가고 제목에 [주최]가 붙는다(알림이 붙인다). 받는 사람은 아직 주최자 쪽 로그인이 없을 수 있어 운영진 계정이
 * 아니라 초대받은 이메일을 직접 연락처로 넘긴다. 링크는 주최 화면({@value #ORGANIZER_PAGE_PATH})을 연다 — 로그인하거나 로그인을 만들면 내 주최
 * 홈의 "받은 초대"에서 수락한다(docs/13 "3. 주최 관리 — 받은 초대"). 수락은 초대받은 이메일의 운영진 로그인으로만 되므로 링크에 비밀 값을
 * 담지 않는다.
 */
final class OperatorInvitationNotice {

    /** 주최 화면의 시작 경로. 주최 화면은 /organizer 아래에 있다(web/src/app/organizer). */
    static final String ORGANIZER_PAGE_PATH = "/organizer";

    private OperatorInvitationNotice() {
    }

    static SendNotificationCommand commandFor(OrganizerName organizerName, StaffInvitation invitation,
            WebAddress webAddress) {
        return new SendNotificationCommand(NotificationRecipient.contact(ContactPoint.emailOnly(invitation.inviteeEmail())),
                NotificationKind.OPERATOR_INVITED,
                new NotificationContent(titleOf(organizerName), bodyOf(organizerName, invitation, webAddress)));
    }

    static String titleOf(OrganizerName organizerName) {
        return "'%s'에서 운영자로 초대했습니다".formatted(organizerName.value());
    }

    private static String bodyOf(OrganizerName organizerName, StaffInvitation invitation, WebAddress webAddress) {
        return """
                '%s'의 소유자가 이 이메일(%s)로 운영자 초대를 보냈습니다.
                이 이메일로 주최자 쪽 로그인을 만들거나 이미 있는 로그인으로 들어가 '받은 초대'에서 수락하면 운영자가 됩니다.
                %s

                초대는 %d일 동안 수락할 수 있습니다.
                초대받을 일이 없었다면 이 메일을 무시해 주세요.
                """.formatted(organizerName.value(), invitation.inviteeEmail().value(),
                webAddress.page(ORGANIZER_PAGE_PATH), ProposedPolicy.STAFF_INVITATION_VALIDITY.toDays());
    }
}
