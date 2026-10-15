package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.common.KoreanTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 박정훈(docs/15)의 소속 — 극단 열두달 소유자 · 2019~, 늦여름 운영자 · 2023~2024 (docs/01 "운영진 프로필" 예시).
 */
@DisplayName("한 사람의 소속과 운영진 로그인 탈퇴 (docs/01 \"탈퇴와 주최자 닫기\")")
class StaffAffiliationsTest {

    private static final long JUNGHOON = 1L;
    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 30L;
    private static final long NEW_TEAM = 41L;

    private static final Instant Y2019 = at(2019);
    private static final Instant Y2023 = at(2023);
    private static final Instant Y2024 = at(2024);
    private static final Instant Y2026 = at(2026);

    private static Instant at(int year) {
        return KoreanTime.toInstant(LocalDateTime.of(year, 3, 1, 10, 0));
    }

    private static StaffMembership owner(long organizerId, Instant startedAt, Instant endedAt) {
        return new StaffMembership(organizerId, JUNGHOON, StaffRole.OWNER, startedAt, endedAt);
    }

    private static StaffMembership operator(long organizerId, Instant startedAt, Instant endedAt) {
        return new StaffMembership(organizerId, JUNGHOON, StaffRole.OPERATOR, startedAt, endedAt);
    }

    @Test
    @DisplayName("한 사람이 여러 주최자에 소속될 수 있고, 주최자마다 역할이 따로다")
    void belongsToManyOrganizers() {
        StaffAffiliations affiliations = new StaffAffiliations(JUNGHOON, List.of(
                owner(TWELVE_MONTHS, Y2019, null),
                operator(NEW_TEAM, Y2026, null)));

        assertThat(affiliations.activeMemberships()).hasSize(2);
        assertThat(affiliations.activeOrganizerIds()).containsExactly(TWELVE_MONTHS, NEW_TEAM);
        assertThat(affiliations.ownedOrganizerIds()).containsExactly(TWELVE_MONTHS);
    }

    @Test
    @DisplayName("지난 소속도 이력으로 갖는다 — 늦여름 운영자 · 2023~2024")
    void keepsPastMemberships() {
        StaffAffiliations affiliations = new StaffAffiliations(JUNGHOON, List.of(
                owner(TWELVE_MONTHS, Y2019, null),
                operator(LATE_SUMMER, Y2023, Y2024)));

        assertThat(affiliations.memberships()).hasSize(2);
        assertThat(affiliations.activeOrganizerIds()).containsExactly(TWELVE_MONTHS);
    }

    @Test
    @DisplayName("운영자로만 소속돼 있으면 언제든 탈퇴할 수 있다 — 소속 주최자에서 빠진다")
    void operatorCanWithdraw() {
        StaffAffiliations affiliations = new StaffAffiliations(JUNGHOON, List.of(
                operator(LATE_SUMMER, Y2023, null),
                operator(NEW_TEAM, Y2026, null)));

        assertThat(affiliations.canWithdraw()).isTrue();
        assertThatNoException().isThrownBy(affiliations::requireWithdrawable);
        assertThat(affiliations.activeOrganizerIds()).containsExactly(LATE_SUMMER, NEW_TEAM);
    }

    @Test
    @DisplayName("소유자인 주최자가 하나라도 있으면 탈퇴할 수 없다 — '먼저 소유자를 넘기거나 주최자를 닫아야 합니다'")
    void ownerCannotWithdraw() {
        StaffAffiliations affiliations = new StaffAffiliations(JUNGHOON, List.of(
                owner(TWELVE_MONTHS, Y2019, null),
                operator(NEW_TEAM, Y2026, null)));

        assertThat(affiliations.canWithdraw()).isFalse();
        assertRejected(affiliations::requireWithdrawable, OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST);
    }

    @Test
    @DisplayName("소유자를 넘긴 뒤에는 탈퇴할 수 있다 — 넘긴 사람은 운영자로 남아 있다")
    void canWithdrawAfterTransfer() {
        StaffAffiliations affiliations = new StaffAffiliations(JUNGHOON, List.of(
                owner(TWELVE_MONTHS, Y2019, Y2026),
                operator(TWELVE_MONTHS, Y2026, null)));

        assertThat(affiliations.canWithdraw()).isTrue();
        assertThat(affiliations.activeOrganizerIds()).containsExactly(TWELVE_MONTHS);
    }

    @Test
    @DisplayName("주최자를 닫은 뒤에는 탈퇴할 수 있다 — 닫으면 운영진이 전원 빠진다")
    void canWithdrawAfterClosing() {
        StaffAffiliations affiliations = new StaffAffiliations(JUNGHOON, List.of(owner(TWELVE_MONTHS, Y2019, Y2026)));

        assertThat(affiliations.canWithdraw()).isTrue();
        assertThat(affiliations.activeOrganizerIds()).isEmpty();
    }

    @Test
    @DisplayName("다른 사람의 소속이 섞이거나 한 주최자에 지금 소속이 둘이면 만들 수 없다")
    void rejectsInconsistentMemberships() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StaffAffiliations(JUNGHOON, List.of(
                new StaffMembership(TWELVE_MONTHS, 2L, StaffRole.OPERATOR, Y2019, null))));
        assertThatIllegalArgumentException().isThrownBy(() -> new StaffAffiliations(JUNGHOON, List.of(
                owner(TWELVE_MONTHS, Y2019, null),
                operator(TWELVE_MONTHS, Y2023, null))));
    }
}
