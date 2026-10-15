package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.application.notification.port.out.OrganizerStaffContactPort;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import com.ticketon.domain.organizer.StaffRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Component;

/**
 * 주최자 운영진 연락처 조회 어댑터 — 소속 표(staff_membership — V8)의 지금 소속과 운영진 로그인 표(staff_account — V5)의 이메일을 잇는다.
 * <p>
 * 끝나지 않은 소속(ended_at이 비어 있음)만 본다 — 나간 운영진은 받지 않는다. 운영진 로그인을 탈퇴한 사람도 받지 않는다 — 탈퇴하면 소속이
 * 함께 끝나 원래 지금 소속이 없지만, 이메일을 파기한 계정 하나 때문에 그 주최자의 메일이 통째로 실패하지 않게 거른다. 소유자는 소속 표의 유일
 * 색인으로 많아야 한 명이다.
 * 운영자는 소속을 시작한 순서로 담는다. 운영진 로그인 이메일로 보낸다 — 주최자 쪽 알림은 전부 이메일이다(docs/12 "주최자 대상").
 * <p>
 * 지금 소유자가 없으면 비어 있다. 없는 주최자뿐 아니라 운영진이 아무도 없는 주최자 — 심사 중(소유자 소속은 승인 때 시작), 닫힘·영구 정지
 * (운영진이 전원 빠짐) — 도 그렇다. 이때의 알림(신청·다시 열기의 승인·반려)은 주최자 id가 아니라 그 사람의 연락처로 보낸다
 * (OrganizerRecipientScope.INDIVIDUAL).
 */
@Component
public class OrganizerStaffContactPersistenceAdapter implements OrganizerStaffContactPort {

    private final DSLContext dsl;

    public OrganizerStaffContactPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<OrganizerStaffContacts> findStaffContacts(long organizerId) {
        EmailAddress owner = null;
        List<EmailAddress> operators = new ArrayList<>();
        for (Record2<String, String> member : currentStaffOf(organizerId)) {
            EmailAddress email = new EmailAddress(member.value2());
            switch (StaffRole.valueOf(member.value1())) {
                case OWNER -> owner = email;
                case OPERATOR -> operators.add(email);
            }
        }
        return owner == null ? Optional.empty() : Optional.of(new OrganizerStaffContacts(owner, operators));
    }

    /** 그 주최자에 지금 소속된, 탈퇴하지 않은 운영진의 역할과 운영진 로그인 이메일 — 소속을 시작한 순서. */
    private List<Record2<String, String>> currentStaffOf(long organizerId) {
        return dsl.select(STAFF_MEMBERSHIP.ROLE, STAFF_ACCOUNT.EMAIL)
                .from(STAFF_MEMBERSHIP)
                .join(STAFF_ACCOUNT).on(STAFF_ACCOUNT.ID.eq(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID))
                .where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId))
                .and(STAFF_MEMBERSHIP.ENDED_AT.isNull())
                .and(STAFF_ACCOUNT.STATUS.ne(StaffAccountStatus.WITHDRAWN.name()))
                .orderBy(STAFF_MEMBERSHIP.STARTED_AT, STAFF_MEMBERSHIP.ID)
                .fetch();
    }
}
