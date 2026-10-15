package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER_NAME_CHANGE;

import com.ticketon.application.organizer.port.out.OrganizerNameChangeRepository;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.OrganizerNameChangeEntry;
import com.ticketon.persistence.jooq.tables.records.OrganizerNameChangeRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 주최자명 변경 이력 저장소(organizer_name_change — V17) 어댑터. 이름을 바꾼 한 건이 한 줄이고, 바꿀 때의 두 이름을 그대로 남긴다.
 *
 * <h2>두 관리자가 거의 동시에 확인하기 — 확인 경합</h2>
 * 확인은 {@code UPDATE … WHERE confirmed_at IS NULL}로 남긴다. 먼저 바꾼 쪽이 커밋할 때까지 늦은 쪽이 기다렸다가, 바뀐 줄이 없어
 * {@link ConflictException}(409)이 된다.
 */
@Component
public class OrganizerNameChangePersistenceAdapter implements OrganizerNameChangeRepository {

    private final DSLContext dsl;

    public OrganizerNameChangePersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public OrganizerNameChangeEntry add(long organizerId, OrganizerNameChange change) {
        long id = dsl.insertInto(ORGANIZER_NAME_CHANGE)
                .set(ORGANIZER_NAME_CHANGE.ORGANIZER_ID, organizerId)
                .set(ORGANIZER_NAME_CHANGE.PREVIOUS_NAME, change.previousName().value())
                .set(ORGANIZER_NAME_CHANGE.NEW_NAME, change.newName().value())
                .set(ORGANIZER_NAME_CHANGE.CHANGED_BY_STAFF_ACCOUNT_ID, change.changedByStaffAccountId())
                .set(ORGANIZER_NAME_CHANGE.CHANGED_AT, toDatabaseTime(change.changedAt()))
                .returningResult(ORGANIZER_NAME_CHANGE.ID)
                .fetchSingle()
                .value1();
        return OrganizerNameChangeEntry.restore(id, organizerId, change, null, null);
    }

    @Override
    public Optional<OrganizerNameChangeEntry> findById(long id) {
        return dsl.selectFrom(ORGANIZER_NAME_CHANGE)
                .where(ORGANIZER_NAME_CHANGE.ID.eq(id))
                .fetchOptional()
                .map(OrganizerNameChangePersistenceAdapter::toEntry);
    }

    @Override
    public void saveConfirmation(OrganizerNameChangeEntry confirmedEntry) {
        Instant confirmedAt = confirmedEntry.confirmedAt()
                .orElseThrow(() -> new IllegalArgumentException("확인하지 않은 주최자명 변경입니다: " + confirmedEntry.id()));
        AccountRef confirmedBy = confirmedEntry.confirmedBy().orElseThrow();
        int updated = dsl.update(ORGANIZER_NAME_CHANGE)
                .set(ORGANIZER_NAME_CHANGE.CONFIRMED_AT, toDatabaseTime(confirmedAt))
                .set(ORGANIZER_NAME_CHANGE.CONFIRMED_BY_ADMIN_ACCOUNT_ID, confirmedBy.id())
                .where(ORGANIZER_NAME_CHANGE.ID.eq(confirmedEntry.id()))
                .and(ORGANIZER_NAME_CHANGE.CONFIRMED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED,
                    "확인 경합 — 주최자명 변경 " + confirmedEntry.id() + "을 다른 관리자가 먼저 확인함");
        }
    }

    private static OrganizerNameChangeEntry toEntry(OrganizerNameChangeRecord row) {
        OrganizerNameChange change = new OrganizerNameChange(new OrganizerName(row.getPreviousName()),
                new OrganizerName(row.getNewName()), row.getChangedByStaffAccountId(), row.getChangedAt().toInstant());
        Long confirmedByAdminAccountId = row.getConfirmedByAdminAccountId();
        return OrganizerNameChangeEntry.restore(row.getId(), row.getOrganizerId(), change,
                row.getConfirmedAt() == null ? null : row.getConfirmedAt().toInstant(),
                confirmedByAdminAccountId == null ? null : AccountRef.of(AccountKind.ADMIN, confirmedByAdminAccountId));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
