package com.ticketon.application.admin.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.notification.OrganizerRecipientScope;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.RejectionReason;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 소유자 이전 신청 반려 메일 — 사유를 담아 <b>신청한 운영자와 그 주최자의 운영진 전원</b>에게 한 사람에 한 통씩 보낸다
 * ({@link NotificationKind#OWNERSHIP_CLAIM_REJECTED}, #27 "결과를 신청자와 운영진 전원에게", docs/12 "주최자 대상 — 받는 사람").
 * <ul>
 *   <li>신청한 사람은 결과 전에 주최자에서 나갔어도 받는다. 아직 운영진이면 운영진 전원과 겹치므로 한 번만 간다.</li>
 *   <li>운영진 로그인을 탈퇴한 신청자는 이메일을 파기했으므로 보내지 않는다.</li>
 * </ul>
 * 소유자는 그대로다. 사람은 공개 프로필의 닉네임으로 적는다. 주최자 쪽 알림이라 제목에 [주최]가 붙는다(알림이 붙인다).
 */
final class OwnershipClaimRejectedNotice {

    private OwnershipClaimRejectedNotice() {
    }

    /**
     * 받는 사람 — 신청한 사람(탈퇴하지 않았으면)이 맨 앞, 그다음 운영진 전원(소유자 · 운영자). 같은 이메일은 한 번만 넣는다.
     *
     * @param claimant      신청한 사람의 운영진 로그인
     * @param staffContacts 그 주최자의 지금 운영진 연락처. 운영진이 남지 않았으면 비어 있다.
     */
    static List<EmailAddress> addresseesOf(StaffAccount claimant, Optional<OrganizerStaffContacts> staffContacts) {
        Set<EmailAddress> addressees = new LinkedHashSet<>();
        if (!claimant.isWithdrawn()) {
            addressees.add(claimant.personalInformation().email());
        }
        staffContacts.ifPresent(contacts -> addressees.addAll(contacts.emailsFor(OrganizerRecipientScope.ALL_STAFF)));
        return List.copyOf(addressees);
    }

    static SendNotificationCommand commandFor(EmailAddress addressee, OrganizerName organizerName, Nickname claimant,
            RejectionReason reason) {
        return new SendNotificationCommand(NotificationRecipient.contact(ContactPoint.emailOnly(addressee)),
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
