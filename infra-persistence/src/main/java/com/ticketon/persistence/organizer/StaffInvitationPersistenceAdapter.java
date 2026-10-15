package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_INVITATION;

import com.ticketon.application.organizer.port.out.StaffInvitationRepository;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import com.ticketon.persistence.jooq.tables.records.StaffInvitationRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 운영자 초대 저장소(staff_invitation — V12) 어댑터. 초대 한 건이 한 줄이다.
 *
 * <h2>같은 이메일로 거의 동시에 두 번 초대 — 초대 경합</h2>
 * 초대는 "답을 기다리는 초대가 있는지 확인 → 저장" 두 단계라 둘 다 확인을 통과할 수 있다. 마지막 판정은 표의 유일 색인(한 주최자 · 한 이메일에
 * 답을 기다리는 초대 하나)이 한다. 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지 않으면 진 쪽이라
 * {@link ConflictException}(409)이 된다.
 *
 * <h2>같은 초대에 거의 동시에 두 번 답 — 수락 경합</h2>
 * 답은 {@code UPDATE … WHERE status = 'PENDING'}으로 저장하고, 바뀐 줄이 없으면 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 바꾼
 * 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 줄의 잠금을 기다렸다가 바뀐 상태로 조건을 다시 보므로 둘 다 바뀌는 일은 없다. 다시 보내기도 같다
 * — 표에서 아직 답을 기다리고 유효 기간이 지나 있을 때만 바꾼다.
 * <p>
 * 이메일의 대소문자는 운영진 로그인(V5)과 같이 SQL의 {@code lower()}로 맞춘 칸으로 비교한다.
 */
@Component
public class StaffInvitationPersistenceAdapter implements StaffInvitationRepository {

    private final DSLContext dsl;

    public StaffInvitationPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public StaffInvitation add(StaffInvitation newInvitation) {
        if (newInvitation.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 초대입니다: " + newInvitation.id().getAsLong());
        }
        if (newInvitation.status() != StaffInvitationStatus.PENDING) {
            throw new IllegalArgumentException("막 보낸 초대는 답을 기다려야 합니다: " + newInvitation.status());
        }
        long id = dsl.insertInto(STAFF_INVITATION)
                .set(STAFF_INVITATION.ORGANIZER_ID, newInvitation.organizerId())
                .set(STAFF_INVITATION.INVITEE_EMAIL, newInvitation.inviteeEmail().value())
                .set(STAFF_INVITATION.INVITEE_EMAIL_LOWERCASE, lowercase(newInvitation.inviteeEmail()))
                .set(STAFF_INVITATION.INVITED_BY_STAFF_ACCOUNT_ID, newInvitation.invitedByStaffAccountId())
                .set(STAFF_INVITATION.INVITED_AT, toDatabaseTime(newInvitation.invitedAt()))
                .set(STAFF_INVITATION.EXPIRES_AT, toDatabaseTime(newInvitation.expiresAt()))
                .set(STAFF_INVITATION.STATUS, newInvitation.status().name())
                .onConflictDoNothing()
                .returningResult(STAFF_INVITATION.ID)
                .fetchOptional()
                .map(row -> row.value1())
                .orElseThrow(() -> new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING,
                        "동시 초대 경합 — 주최자 " + newInvitation.organizerId() + "의 같은 이메일 초대가 먼저 저장됨"));
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 초대를 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<StaffInvitation> findById(long id) {
        return dsl.selectFrom(STAFF_INVITATION)
                .where(STAFF_INVITATION.ID.eq(id))
                .fetchOptional()
                .map(StaffInvitationPersistenceAdapter::toInvitation);
    }

    @Override
    public Optional<StaffInvitation> findPending(long organizerId, EmailAddress inviteeEmail) {
        return dsl.selectFrom(STAFF_INVITATION)
                .where(STAFF_INVITATION.ORGANIZER_ID.eq(organizerId))
                .and(STAFF_INVITATION.INVITEE_EMAIL_LOWERCASE.eq(lowercase(inviteeEmail)))
                .and(STAFF_INVITATION.STATUS.eq(StaffInvitationStatus.PENDING.name()))
                .fetchOptional()
                .map(StaffInvitationPersistenceAdapter::toInvitation);
    }

    @Override
    public void saveReinvitation(StaffInvitation reinvitedInvitation) {
        long id = idOf(reinvitedInvitation);
        if (reinvitedInvitation.status() != StaffInvitationStatus.PENDING) {
            throw new IllegalArgumentException("답을 기다리는 초대만 다시 보냅니다: 초대 " + id);
        }
        OffsetDateTime reinvitedAt = toDatabaseTime(reinvitedInvitation.invitedAt());
        int updated = dsl.update(STAFF_INVITATION)
                .set(STAFF_INVITATION.INVITED_BY_STAFF_ACCOUNT_ID, reinvitedInvitation.invitedByStaffAccountId())
                .set(STAFF_INVITATION.INVITED_AT, reinvitedAt)
                .set(STAFF_INVITATION.EXPIRES_AT, toDatabaseTime(reinvitedInvitation.expiresAt()))
                .where(STAFF_INVITATION.ID.eq(id))
                .and(STAFF_INVITATION.STATUS.eq(StaffInvitationStatus.PENDING.name()))
                .and(STAFF_INVITATION.EXPIRES_AT.le(reinvitedAt))
                .execute();
        if (updated == 0) {
            throw lostReinvitationRace(id);
        }
    }

    @Override
    public void saveAnswer(StaffInvitation answeredInvitation) {
        long id = idOf(answeredInvitation);
        if (answeredInvitation.status() == StaffInvitationStatus.PENDING) {
            throw new IllegalArgumentException("아직 답하지 않은 초대입니다: 초대 " + id);
        }
        int updated = dsl.update(STAFF_INVITATION)
                .set(STAFF_INVITATION.STATUS, answeredInvitation.status().name())
                .set(STAFF_INVITATION.ANSWERED_AT, toDatabaseTime(answeredInvitation.answeredAt().orElseThrow()))
                .set(STAFF_INVITATION.ANSWERED_BY_STAFF_ACCOUNT_ID,
                        answeredInvitation.answeredByStaffAccountId().orElseThrow())
                .where(STAFF_INVITATION.ID.eq(id))
                .and(STAFF_INVITATION.STATUS.eq(StaffInvitationStatus.PENDING.name()))
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED,
                    "수락 경합 — 초대 " + id + "에 다른 요청이 먼저 답함");
        }
    }

    /** 다시 보내기에 진 까닭 — 그 사이 답했으면 답한 초대, 아니면 다른 요청이 먼저 다시 보낸 것이다. */
    private ConflictException lostReinvitationRace(long id) {
        boolean stillPending = dsl.fetchExists(STAFF_INVITATION,
                STAFF_INVITATION.ID.eq(id).and(STAFF_INVITATION.STATUS.eq(StaffInvitationStatus.PENDING.name())));
        String reasonForLog = "다시 보내기 경합 — 초대 " + id;
        return stillPending ? new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING, reasonForLog)
                : new ConflictException(OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED, reasonForLog);
    }

    private static long idOf(StaffInvitation invitation) {
        return invitation.id().orElseThrow(() -> new IllegalArgumentException("저장 전 초대입니다"));
    }

    private static StaffInvitation toInvitation(StaffInvitationRecord row) {
        return StaffInvitation.restore(row.getId(), row.getOrganizerId(), new EmailAddress(row.getInviteeEmail()),
                row.getInvitedByStaffAccountId(), row.getInvitedAt().toInstant(), row.getExpiresAt().toInstant(),
                StaffInvitationStatus.valueOf(row.getStatus()), toInstantOrNull(row.getAnsweredAt()),
                row.getAnsweredByStaffAccountId());
    }

    private static Field<String> lowercase(EmailAddress email) {
        return DSL.lower(DSL.val(email.value()));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstantOrNull(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
