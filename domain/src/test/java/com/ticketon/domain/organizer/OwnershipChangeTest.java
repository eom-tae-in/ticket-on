package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 극단 열두달의 대표 박정훈(1)이 기획 단원(2)에게 소유자를 넘긴 한 건을 관리자가 사후 확인한다. */
@DisplayName("소유자 변경 이력 — 관리자 사후 확인 (docs/12 \"소유자가 바뀌면 관리자에게도 알립니다\")")
class OwnershipChangeTest {

    private static final Instant TRANSFERRED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant CONFIRMED_AT = TRANSFERRED_AT.plusSeconds(3_600);

    private static final AccountRef ADMIN = AccountRef.of(AccountKind.ADMIN, 7);

    private static final OwnershipTransfer TRANSFER = new OwnershipTransfer(12, 1, 2, TRANSFERRED_AT);

    private static OwnershipChange unconfirmed() {
        return OwnershipChange.restore(9, TRANSFER, null, null);
    }

    @Test
    @DisplayName("확인 전에는 처리 목록에 남아 있고, 관리자가 확인하면 확인 일시와 확인한 관리자가 남는다")
    void adminConfirms() {
        OwnershipChange change = unconfirmed();
        assertThat(change.isConfirmed()).isFalse();
        assertThat(change.confirmedAt()).isEmpty();

        change.confirm(ADMIN, CONFIRMED_AT);

        assertThat(change.isConfirmed()).isTrue();
        assertThat(change.confirmedAt()).contains(CONFIRMED_AT);
        assertThat(change.confirmedBy()).contains(ADMIN);
        assertThat(change.transfer()).isEqualTo(TRANSFER);
        assertThat(change.id()).isEqualTo(9);
    }

    @Test
    @DisplayName("확인은 한 번뿐이다 — 이미 확인한 변경을 다시 확인하면 OWNERSHIP_CHANGE_ALREADY_CONFIRMED이고 먼저 확인한 기록이 그대로다")
    void confirmsOnlyOnce() {
        OwnershipChange change = unconfirmed();
        change.confirm(ADMIN, CONFIRMED_AT);
        AccountRef anotherAdmin = AccountRef.of(AccountKind.ADMIN, 8);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> change.confirm(anotherAdmin, CONFIRMED_AT.plusSeconds(60)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED));
        assertThat(change.confirmedBy()).contains(ADMIN);
        assertThat(change.confirmedAt()).contains(CONFIRMED_AT);
    }

    @Test
    @DisplayName("관리자만 확인하고, 넘긴 일시보다 앞서 확인할 수 없다")
    void onlyAdminAfterTransfer() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> unconfirmed().confirm(AccountRef.of(AccountKind.STAFF, 1), CONFIRMED_AT));
        assertThatIllegalArgumentException().isThrownBy(
                () -> unconfirmed().confirm(ADMIN, TRANSFERRED_AT.minusSeconds(1)));
    }

    @Test
    @DisplayName("저장소에서 되살릴 때 확인 일시와 확인한 관리자는 함께 있거나 함께 없어야 하고, id는 1 이상이다")
    void restoreRules() {
        OwnershipChange confirmed = OwnershipChange.restore(9, TRANSFER, CONFIRMED_AT, ADMIN);
        assertThat(confirmed.isConfirmed()).isTrue();

        assertThatIllegalArgumentException().isThrownBy(() -> OwnershipChange.restore(9, TRANSFER, CONFIRMED_AT, null));
        assertThatIllegalArgumentException().isThrownBy(() -> OwnershipChange.restore(9, TRANSFER, null, ADMIN));
        assertThatIllegalArgumentException().isThrownBy(() -> OwnershipChange.restore(0, TRANSFER, null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> OwnershipChange.restore(9, TRANSFER, TRANSFERRED_AT.minusSeconds(1), ADMIN));
    }
}
