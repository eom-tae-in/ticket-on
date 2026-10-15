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

/** 극단 열두달(12)의 대표 박정훈(1)이 주최자명을 "열두달 프로젝트"로 바꾼 한 건을 관리자가 처리 목록에서 사후 확인한다. */
@DisplayName("주최자명 변경 이력 — 관리자 사후 확인 (docs/12 \"관리자 대상 — 주최자명 변경 (사후 확인용)\")")
class OrganizerNameChangeEntryTest {

    private static final Instant CHANGED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant CONFIRMED_AT = CHANGED_AT.plusSeconds(3_600);

    private static final AccountRef ADMIN = AccountRef.of(AccountKind.ADMIN, 7);

    private static final OrganizerNameChange CHANGE = new OrganizerNameChange(OrganizerName.of("극단 열두달"),
            OrganizerName.of("열두달 프로젝트"), 1, CHANGED_AT);

    private static OrganizerNameChangeEntry unconfirmed() {
        return OrganizerNameChangeEntry.restore(5, 12, CHANGE, null, null);
    }

    @Test
    @DisplayName("확인 전에는 처리 목록에 남아 있고, 관리자가 확인하면 확인 일시와 확인한 관리자가 남는다 — 바꾼 한 건은 그대로다")
    void adminConfirms() {
        OrganizerNameChangeEntry entry = unconfirmed();
        assertThat(entry.isConfirmed()).isFalse();
        assertThat(entry.confirmedAt()).isEmpty();
        assertThat(entry.confirmedBy()).isEmpty();

        entry.confirm(ADMIN, CONFIRMED_AT);

        assertThat(entry.isConfirmed()).isTrue();
        assertThat(entry.confirmedAt()).contains(CONFIRMED_AT);
        assertThat(entry.confirmedBy()).contains(ADMIN);
        assertThat(entry.change()).isEqualTo(CHANGE);
        assertThat(entry.id()).isEqualTo(5);
        assertThat(entry.organizerId()).isEqualTo(12);
    }

    @Test
    @DisplayName("확인은 한 번뿐이다 — 이미 확인한 변경을 다시 확인하면 ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED이고 먼저 확인한 기록이 그대로다")
    void confirmsOnlyOnce() {
        OrganizerNameChangeEntry entry = unconfirmed();
        entry.confirm(ADMIN, CONFIRMED_AT);
        AccountRef anotherAdmin = AccountRef.of(AccountKind.ADMIN, 8);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> entry.confirm(anotherAdmin, CONFIRMED_AT.plusSeconds(60)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED));
        assertThat(entry.confirmedBy()).contains(ADMIN);
        assertThat(entry.confirmedAt()).contains(CONFIRMED_AT);
    }

    @Test
    @DisplayName("관리자만 확인하고, 바꾼 일시보다 앞서 확인할 수 없다 — 바꾼 바로 그 시각에는 확인할 수 있다")
    void onlyAdminAfterChange() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> unconfirmed().confirm(AccountRef.of(AccountKind.STAFF, 1), CONFIRMED_AT));
        assertThatIllegalArgumentException().isThrownBy(
                () -> unconfirmed().confirm(ADMIN, CHANGED_AT.minusSeconds(1)));

        OrganizerNameChangeEntry entry = unconfirmed();
        entry.confirm(ADMIN, CHANGED_AT);
        assertThat(entry.confirmedAt()).contains(CHANGED_AT);
    }

    @Test
    @DisplayName("저장소에서 되살릴 때 확인 일시와 확인한 관리자는 함께 있거나 함께 없어야 하고, id와 주최자 id는 1 이상이다")
    void restoreRules() {
        assertThat(OrganizerNameChangeEntry.restore(5, 12, CHANGE, CONFIRMED_AT, ADMIN).isConfirmed()).isTrue();

        assertThatIllegalArgumentException().isThrownBy(
                () -> OrganizerNameChangeEntry.restore(5, 12, CHANGE, CONFIRMED_AT, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> OrganizerNameChangeEntry.restore(5, 12, CHANGE, null, ADMIN));
        assertThatIllegalArgumentException().isThrownBy(
                () -> OrganizerNameChangeEntry.restore(0, 12, CHANGE, null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> OrganizerNameChangeEntry.restore(5, 0, CHANGE, null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> OrganizerNameChangeEntry.restore(5, 12, CHANGE, CHANGED_AT.minusSeconds(1), ADMIN));
        assertThatIllegalArgumentException().isThrownBy(
                () -> OrganizerNameChangeEntry.restore(5, 12, CHANGE, CONFIRMED_AT, AccountRef.of(AccountKind.STAFF, 1)));
    }
}
