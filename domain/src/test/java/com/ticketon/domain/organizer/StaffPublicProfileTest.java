package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffIntroduction;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.organizer.StaffPublicProfile.ParticipatedOrganizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 박정훈(docs/15)의 운영진 프로필 — docs/01 "운영진 프로필" 예시 그대로다.
 * <pre>
 * 조용한고래
 * 극단에서 연출하고 있습니다
 * 참여한 주최자
 * 극단 열두달 · 소유자 · 2019~
 * 늦여름 · 운영자 · 2023~2024 (활동 종료)
 * </pre>
 */
@DisplayName("운영진 프로필 (docs/01 \"공개 페이지 — 운영진 프로필\")")
class StaffPublicProfileTest {

    private static final long JUNGHOON = 7L;
    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 30L;
    private static final long SPRING_DAY = 35L;
    private static final long SUSPENDED_TEAM = 38L;
    private static final long NEW_TEAM = 41L;

    private static final Instant MARCH_2019 = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));
    private static final Instant MAY_2023 = KoreanTime.toInstant(LocalDateTime.of(2023, 5, 1, 10, 0));
    private static final Instant NOVEMBER_2024 = KoreanTime.toInstant(LocalDateTime.of(2024, 11, 30, 18, 0));
    private static final Instant JANUARY_2026 = KoreanTime.toInstant(LocalDateTime.of(2026, 1, 10, 10, 0));

    private static final Organizer TWELVE_MONTHS_ORGANIZER = organizer(TWELVE_MONTHS, "극단 열두달", OrganizerStatus.ACTIVE);
    private static final Organizer LATE_SUMMER_ORGANIZER = organizer(LATE_SUMMER, "늦여름", OrganizerStatus.ACTIVE);

    @Nested
    @DisplayName("보이는 것 — 닉네임 · 한 줄 소개 · 참여한 주최자(이름 · 역할 · 기간)")
    class Shown {

        @Test
        @DisplayName("docs/01 예시: 극단 열두달 · 소유자 · 2019~ / 늦여름 · 운영자 · 2023~2024 (활동 종료)")
        void documentExample() {
            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(), affiliations(
                            owner(TWELVE_MONTHS, MARCH_2019, null),
                            operator(LATE_SUMMER, MAY_2023, NOVEMBER_2024)),
                    List.of(TWELVE_MONTHS_ORGANIZER, LATE_SUMMER_ORGANIZER)).orElseThrow();

            assertThat(profile.staffAccountId()).isEqualTo(JUNGHOON);
            assertThat(profile.nickname()).isEqualTo(Nickname.of("조용한고래"));
            assertThat(profile.introduction()).contains(new StaffIntroduction("극단에서 연출하고 있습니다"));
            assertThat(profile.participatedOrganizers()).containsExactly(
                    new ParticipatedOrganizer(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), StaffRole.OWNER, MARCH_2019,
                            null, false),
                    new ParticipatedOrganizer(LATE_SUMMER, OrganizerName.of("늦여름"), StaffRole.OPERATOR, MAY_2023,
                            NOVEMBER_2024, true));
        }

        @Test
        @DisplayName("사진 대신 닉네임 첫 글자 + 사람마다 정해진 색 — 조용한고래(계정 7)는 '조'와 팔레트 7번")
        void initialAvatar() {
            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(),
                    affiliations(owner(TWELVE_MONTHS, MARCH_2019, null)), List.of(TWELVE_MONTHS_ORGANIZER)).orElseThrow();

            assertThat(profile.initialAvatar()).isEqualTo(new InitialAvatar("조", 7));
        }

        @Test
        @DisplayName("소개를 적지 않았으면 소개는 비어 있다")
        void withoutIntroduction() {
            StaffAccount account = staffAccount(JUNGHOON, null);

            StaffPublicProfile profile = StaffPublicProfile.of(account,
                    affiliations(owner(TWELVE_MONTHS, MARCH_2019, null)), List.of(TWELVE_MONTHS_ORGANIZER)).orElseThrow();

            assertThat(profile.introduction()).isEmpty();
        }

        @Test
        @DisplayName("소속을 시작한 순서로 보인다 — 받은 소속의 순서와 상관없다")
        void orderedByStart() {
            Organizer newTeam = organizer(NEW_TEAM, "새 프로젝트 팀", OrganizerStatus.ACTIVE);

            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(), affiliations(
                            owner(NEW_TEAM, JANUARY_2026, null),
                            operator(LATE_SUMMER, MAY_2023, NOVEMBER_2024),
                            owner(TWELVE_MONTHS, MARCH_2019, null)),
                    List.of(newTeam, TWELVE_MONTHS_ORGANIZER, LATE_SUMMER_ORGANIZER)).orElseThrow();

            assertThat(profile.participatedOrganizers()).extracting(ParticipatedOrganizer::organizerId)
                    .containsExactly(TWELVE_MONTHS, LATE_SUMMER, NEW_TEAM);
        }

        @Test
        @DisplayName("주최자명은 지금 이름으로 보인다 — 소속은 주최자를 가리키므로 이름을 바꾸면 새 이름이 뜬다")
        void currentOrganizerName() {
            Organizer renamed = organizer(TWELVE_MONTHS, "극단 열두달 플러스", OrganizerStatus.ACTIVE);

            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(),
                    affiliations(owner(TWELVE_MONTHS, MARCH_2019, null)), List.of(renamed)).orElseThrow();

            assertThat(profile.participatedOrganizers().getFirst().organizerName())
                    .isEqualTo(OrganizerName.of("극단 열두달 플러스"));
        }
    }

    @Nested
    @DisplayName("지난 소속과 활동 종료 — 나왔거나 닫힌 주최자")
    class ActivityEnded {

        @Test
        @DisplayName("닫힌 주최자는 이력으로 남고 활동 종료다 — 닫으면 운영진이 전원 빠진다 (docs/01 \"탈퇴와 주최자 닫기\")")
        void closedOrganizerStaysAsEnded() {
            Organizer springDay = organizer(SPRING_DAY, "극단 봄날", OrganizerStatus.CLOSED);

            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(),
                    affiliations(owner(SPRING_DAY, MARCH_2019, NOVEMBER_2024)), List.of(springDay)).orElseThrow();

            assertThat(profile.participatedOrganizers()).containsExactly(new ParticipatedOrganizer(SPRING_DAY,
                    OrganizerName.of("극단 봄날"), StaffRole.OWNER, MARCH_2019, NOVEMBER_2024, true));
        }

        @Test
        @DisplayName("소유자를 넘겨 역할만 바뀌었으면 두 기간이 각각 보이고, 지금도 소속돼 있어 어느 줄도 활동 종료가 아니다")
        void roleChangeIsNotActivityEnd() {
            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(), affiliations(
                            owner(TWELVE_MONTHS, MARCH_2019, JANUARY_2026),
                            operator(TWELVE_MONTHS, JANUARY_2026, null)),
                    List.of(TWELVE_MONTHS_ORGANIZER)).orElseThrow();

            assertThat(profile.participatedOrganizers()).containsExactly(
                    new ParticipatedOrganizer(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), StaffRole.OWNER, MARCH_2019,
                            JANUARY_2026, false),
                    new ParticipatedOrganizer(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), StaffRole.OPERATOR,
                            JANUARY_2026, null, false));
        }
    }

    @Nested
    @DisplayName("빠지는 주최자 — 공개되지 않는 주최자")
    class Excluded {

        @Test
        @DisplayName("영구 정지된 주최자는 프로필 목록에서 빠진다 — 다른 주최자는 그대로다 (docs/01 \"운영진 프로필\")")
        void permanentlySuspendedOrganizerIsExcluded() {
            Organizer suspended = organizer(SUSPENDED_TEAM, "정지된 극단", OrganizerStatus.PERMANENTLY_SUSPENDED);

            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(), affiliations(
                            owner(TWELVE_MONTHS, MARCH_2019, null),
                            operator(SUSPENDED_TEAM, MAY_2023, NOVEMBER_2024)),
                    List.of(TWELVE_MONTHS_ORGANIZER, suspended)).orElseThrow();

            assertThat(profile.participatedOrganizers()).extracting(ParticipatedOrganizer::organizerId)
                    .containsExactly(TWELVE_MONTHS);
        }

        @Test
        @DisplayName("심사 중인 주최자는 공개되지 않아 빠진다")
        void underReviewOrganizerIsExcluded() {
            Organizer underReview = organizer(NEW_TEAM, "심사 중인 팀", OrganizerStatus.UNDER_REVIEW);

            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(), affiliations(
                            owner(TWELVE_MONTHS, MARCH_2019, null),
                            owner(NEW_TEAM, JANUARY_2026, null)),
                    List.of(TWELVE_MONTHS_ORGANIZER, underReview)).orElseThrow();

            assertThat(profile.participatedOrganizers()).extracting(ParticipatedOrganizer::organizerId)
                    .containsExactly(TWELVE_MONTHS);
        }

        @Test
        @DisplayName("영구 정지된 주최자에만 소속됐었어도 운영진이었으므로 프로필은 있고 목록만 빈다")
        void onlySuspendedOrganizerLeavesEmptyList() {
            Organizer suspended = organizer(SUSPENDED_TEAM, "정지된 극단", OrganizerStatus.PERMANENTLY_SUSPENDED);

            StaffPublicProfile profile = StaffPublicProfile.of(junghoon(),
                    affiliations(operator(SUSPENDED_TEAM, MAY_2023, NOVEMBER_2024)), List.of(suspended)).orElseThrow();

            assertThat(profile.participatedOrganizers()).isEmpty();
            assertThat(profile.nickname()).isEqualTo(Nickname.of("조용한고래"));
        }
    }

    @Nested
    @DisplayName("프로필이 없는 사람")
    class NoProfile {

        @Test
        @DisplayName("주최자에 소속된 적이 없는 운영진 로그인은 아직 운영진이 아니라 프로필이 없다 — 운영진이 되면 자동으로 생긴다")
        void neverAffiliated() {
            assertThat(StaffPublicProfile.of(junghoon(), affiliations(), List.of())).isEmpty();
        }

        @Test
        @DisplayName("탈퇴한 운영진 로그인은 프로필이 없다 — 소속 이력이 남아 있어도 (확인 필요 #28)")
        void withdrawn() {
            StaffAccount withdrawn = StaffAccount.restoreWithdrawn(JUNGHOON, Nickname.of("조용한고래"), MARCH_2019,
                    JANUARY_2026);

            assertThat(StaffPublicProfile.of(withdrawn,
                    affiliations(operator(LATE_SUMMER, MAY_2023, JANUARY_2026)), List.of(LATE_SUMMER_ORGANIZER))).isEmpty();
        }
    }

    @Nested
    @DisplayName("잘못 넘긴 값")
    class InvalidInput {

        @Test
        @DisplayName("다른 사람의 소속이거나, 소속된 주최자가 빠졌거나, 저장 전 계정이면 받지 않는다")
        void rejectsInconsistentInput() {
            assertThatIllegalArgumentException().isThrownBy(() -> StaffPublicProfile.of(junghoon(),
                    new StaffAffiliations(99L, List.of()), List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> StaffPublicProfile.of(junghoon(),
                    affiliations(owner(TWELVE_MONTHS, MARCH_2019, null)), List.of(LATE_SUMMER_ORGANIZER)));
            StaffAccount unsaved = StaffAccount.signUp(StaffPersonalInformation.of("박정훈", "junghoon@example.com"),
                    Nickname.of("조용한고래"), new PasswordHash("{test}hashed-password"),
                    EmailAvailability.AVAILABLE, NicknameAvailability.AVAILABLE, MARCH_2019);
            assertThatIllegalArgumentException().isThrownBy(() -> StaffPublicProfile.of(unsaved, affiliations(), List.of()));
        }
    }

    private static StaffAccount junghoon() {
        return staffAccount(JUNGHOON, new StaffIntroduction("극단에서 연출하고 있습니다"));
    }

    private static StaffAccount staffAccount(long id, StaffIntroduction introduction) {
        return StaffAccount.restore(id, StaffPersonalInformation.of("박정훈", "junghoon@example.com"),
                Nickname.of("조용한고래"), introduction, new PasswordHash("{test}hashed-password"), MARCH_2019,
                StaffAccountStatus.ACTIVE, MARCH_2019);
    }

    private static StaffAffiliations affiliations(StaffMembership... memberships) {
        return new StaffAffiliations(JUNGHOON, List.of(memberships));
    }

    private static StaffMembership owner(long organizerId, Instant startedAt, Instant endedAt) {
        return new StaffMembership(organizerId, JUNGHOON, StaffRole.OWNER, startedAt, endedAt);
    }

    private static StaffMembership operator(long organizerId, Instant startedAt, Instant endedAt) {
        return new StaffMembership(organizerId, JUNGHOON, StaffRole.OPERATOR, startedAt, endedAt);
    }

    private static Organizer organizer(long id, String name, OrganizerStatus status) {
        return Organizer.restore(id, OrganizerName.of(name), status, OrganizerPublicProfile.empty());
    }
}
