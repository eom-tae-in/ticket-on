package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;

import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jooq.DSLContext;

/**
 * 주최자 쪽 저장 테스트가 함께 쓰는 줄 넣기 — 운영진 로그인 · 주최자 · 소속을 표에 바로 넣는다. 소속 저장 어댑터는 승인으로 여는 소유자 한 줄만
 * 넣으므로(#23), 운영자·끝난 소속(이력)·닫힌 주최자처럼 아직 기능이 없는 줄은 여기서 넣는다.
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

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
