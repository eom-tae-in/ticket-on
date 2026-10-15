package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CHANGE;

import com.ticketon.application.organizer.port.out.OwnershipChangeRepository;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipChange;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.persistence.jooq.tables.records.OwnershipChangeRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 소유자 변경 이력 저장소(ownership_change — V13) 어댑터. 넘기기 한 건이 한 줄이다.
 *
 * <h2>두 관리자가 거의 동시에 확인하기 — 확인 경합</h2>
 * 확인은 {@code UPDATE … WHERE confirmed_at IS NULL}로 남긴다. 먼저 바꾼 쪽이 커밋할 때까지 늦은 쪽이 기다렸다가, 바뀐 줄이 없어
 * {@link ConflictException}(409)이 된다.
 */
@Component
public class OwnershipChangePersistenceAdapter implements OwnershipChangeRepository {

    private final DSLContext dsl;

    public OwnershipChangePersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public OwnershipChange add(OwnershipTransfer transfer) {
        long id = dsl.insertInto(OWNERSHIP_CHANGE)
                .set(OWNERSHIP_CHANGE.ORGANIZER_ID, transfer.organizerId())
                .set(OWNERSHIP_CHANGE.PREVIOUS_OWNER_STAFF_ACCOUNT_ID, transfer.previousOwnerStaffAccountId())
                .set(OWNERSHIP_CHANGE.NEW_OWNER_STAFF_ACCOUNT_ID, transfer.newOwnerStaffAccountId())
                .set(OWNERSHIP_CHANGE.CHANGED_AT, toDatabaseTime(transfer.transferredAt()))
                .returningResult(OWNERSHIP_CHANGE.ID)
                .fetchSingle()
                .value1();
        return OwnershipChange.restore(id, transfer, null, null);
    }

    @Override
    public Optional<OwnershipChange> findById(long id) {
        return dsl.selectFrom(OWNERSHIP_CHANGE)
                .where(OWNERSHIP_CHANGE.ID.eq(id))
                .fetchOptional()
                .map(OwnershipChangePersistenceAdapter::toChange);
    }

    @Override
    public void saveConfirmation(OwnershipChange confirmedChange) {
        Instant confirmedAt = confirmedChange.confirmedAt()
                .orElseThrow(() -> new IllegalArgumentException("확인하지 않은 소유자 변경입니다: " + confirmedChange.id()));
        AccountRef confirmedBy = confirmedChange.confirmedBy().orElseThrow();
        int updated = dsl.update(OWNERSHIP_CHANGE)
                .set(OWNERSHIP_CHANGE.CONFIRMED_AT, toDatabaseTime(confirmedAt))
                .set(OWNERSHIP_CHANGE.CONFIRMED_BY_ADMIN_ACCOUNT_ID, confirmedBy.id())
                .where(OWNERSHIP_CHANGE.ID.eq(confirmedChange.id()))
                .and(OWNERSHIP_CHANGE.CONFIRMED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED,
                    "확인 경합 — 소유자 변경 " + confirmedChange.id() + "을 다른 관리자가 먼저 확인함");
        }
    }

    private static OwnershipChange toChange(OwnershipChangeRecord change) {
        OwnershipTransfer transfer = new OwnershipTransfer(change.getOrganizerId(),
                change.getPreviousOwnerStaffAccountId(), change.getNewOwnerStaffAccountId(),
                change.getChangedAt().toInstant());
        Long confirmedByAdminAccountId = change.getConfirmedByAdminAccountId();
        return OwnershipChange.restore(change.getId(), transfer,
                change.getConfirmedAt() == null ? null : change.getConfirmedAt().toInstant(),
                confirmedByAdminAccountId == null ? null : AccountRef.of(AccountKind.ADMIN, confirmedByAdminAccountId));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
