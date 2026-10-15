package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_INVITATION;
import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;

/**
 * 주최자 쪽 저장 테스트가 함께 쓰는 줄 넣기 — 운영진 로그인 · 주최자 · 소속 · 운영자 초대를 표에 바로 넣는다. 끝난 소속(이력)·닫힌 주최자·
 * 지난 초대처럼 기능을 거치면 만들기 번거로운 줄은 여기서 넣는다.
 */
public final class OrganizerRows {

    private static final Instant SIGNED_UP_AT = Instant.parse("2026-10-01T01:00:00Z");

    private final DSLContext dsl;

    public OrganizerRows(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 운영진 로그인과 주최자 쪽 표를 모두 비운다. */
    public void clear() {
        dsl.truncate(ORGANIZER).cascade().execute();
        dsl.truncate(STAFF_ACCOUNT).cascade().execute();
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄. 닉네임은 이메일에서 만든다. */
    public long staffAccount(String email) {
        return dsl.insertInto(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.STATUS, "ACTIVE")
                .set(STAFF_ACCOUNT.NAME, "운영진")
                .set(STAFF_ACCOUNT.NICKNAME, email.substring(0, Math.min(email.indexOf('@'), 20)))
                .set(STAFF_ACCOUNT.EMAIL, email)
                .set(STAFF_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase())
                .set(STAFF_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(STAFF_ACCOUNT.SIGNED_UP_AT, toDatabaseTime(SIGNED_UP_AT))
                .set(STAFF_ACCOUNT.EMAIL_VERIFIED_AT, toDatabaseTime(SIGNED_UP_AT))
                .returningResult(STAFF_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    public long organizer(String name, OrganizerStatus status) {
        return dsl.insertInto(ORGANIZER)
                .set(ORGANIZER.NAME, name)
                .set(ORGANIZER.STATUS, status.name())
                .returningResult(ORGANIZER.ID)
                .fetchSingle()
                .value1();
    }

    /** 지금 소속 한 줄. */
    public void currentMembership(long organizerId, long staffAccountId, StaffRole role, Instant startedAt) {
        membership(organizerId, staffAccountId, role, startedAt, null);
    }

    /**
     * 소속 한 줄.
     *
     * @param endedAt 끝난 일시. 지금 소속이면 null.
     */
    public void membership(long organizerId, long staffAccountId, StaffRole role, Instant startedAt, Instant endedAt) {
        dsl.insertInto(STAFF_MEMBERSHIP)
                .set(STAFF_MEMBERSHIP.ORGANIZER_ID, organizerId)
                .set(STAFF_MEMBERSHIP.STAFF_ACCOUNT_ID, staffAccountId)
                .set(STAFF_MEMBERSHIP.ROLE, role.name())
                .set(STAFF_MEMBERSHIP.STARTED_AT, toDatabaseTime(startedAt))
                .set(STAFF_MEMBERSHIP.ENDED_AT, endedAt == null ? null : toDatabaseTime(endedAt))
                .execute();
    }

    /**
     * 운영자 초대 한 줄 — 박정훈 같은 소유자가 그 이메일로 보낸 것.
     *
     * @param status     상태. 답했으면 답한 일시는 보낸 일시 1분 뒤, 답한 사람은 보낸 사람이다(표의 CHECK만 맞춘다).
     * @return 초대 id
     */
    public long invitation(long organizerId, String inviteeEmail, long invitedByStaffAccountId, Instant invitedAt,
            Instant expiresAt, StaffInvitationStatus status) {
        boolean answered = status != StaffInvitationStatus.PENDING;
        return dsl.insertInto(STAFF_INVITATION)
                .set(STAFF_INVITATION.ORGANIZER_ID, organizerId)
                .set(STAFF_INVITATION.INVITEE_EMAIL, inviteeEmail)
                .set(STAFF_INVITATION.INVITEE_EMAIL_LOWERCASE, inviteeEmail.toLowerCase())
                .set(STAFF_INVITATION.INVITED_BY_STAFF_ACCOUNT_ID, invitedByStaffAccountId)
                .set(STAFF_INVITATION.INVITED_AT, toDatabaseTime(invitedAt))
                .set(STAFF_INVITATION.EXPIRES_AT, toDatabaseTime(expiresAt))
                .set(STAFF_INVITATION.STATUS, status.name())
                .set(STAFF_INVITATION.ANSWERED_AT, answered ? toDatabaseTime(invitedAt.plusSeconds(60)) : null)
                .set(STAFF_INVITATION.ANSWERED_BY_STAFF_ACCOUNT_ID, answered ? invitedByStaffAccountId : null)
                .returningResult(STAFF_INVITATION.ID)
                .fetchSingle()
                .value1();
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
