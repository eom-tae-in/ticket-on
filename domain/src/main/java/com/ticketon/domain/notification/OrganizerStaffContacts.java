package com.ticketon.domain.notification;

import com.ticketon.domain.account.EmailAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 한 주최자의 운영진 연락처 — 소유자 1명과 운영자 N명의 이메일(docs/01 "주최자와 운영진").
 * <p>
 * 주최자 id로 보낸 알림을 받는 사람으로 펼칠 때 쓴다(docs/12 "주최자 대상 — 받는 사람"). 주최자 쪽 알림은 전부 이메일이다.
 *
 * @param owner     소유자의 이메일. 소유자는 언제나 1명이다.
 * @param operators 운영자들의 이메일. 없을 수 있다.
 */
public record OrganizerStaffContacts(EmailAddress owner, List<EmailAddress> operators) {

    public OrganizerStaffContacts {
        Objects.requireNonNull(owner, "owner");
        operators = List.copyOf(operators);
    }

    /**
     * 그 범위의 받는 사람 이메일 — 소유자가 맨 앞이다. 같은 주소는 한 번만 넣는다.
     *
     * @throws IllegalArgumentException {@link OrganizerRecipientScope#INDIVIDUAL} — 한 사람에게 가는 알림은 주최자로 펼치지 않는다
     */
    public List<EmailAddress> emailsFor(OrganizerRecipientScope scope) {
        Objects.requireNonNull(scope, "scope");
        return switch (scope) {
            case ALL_STAFF -> ownerAndOperators();
            case OWNER_ONLY -> List.of(owner);
            case INDIVIDUAL -> throw new IllegalArgumentException("한 사람에게 가는 알림은 주최자 운영진으로 펼치지 않습니다");
        };
    }

    private List<EmailAddress> ownerAndOperators() {
        List<EmailAddress> staff = new ArrayList<>();
        staff.add(owner);
        staff.addAll(operators);
        return staff.stream().distinct().toList();
    }
}
