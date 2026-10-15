package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.JUNGHOON;
import static com.ticketon.domain.organizer.OrganizerFixtures.REVIEWED_AT;
import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.domain.account.InitialAvatar;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("주최자 (docs/01, docs/14)")
class OrganizerTest {

    private static final OrganizerName TWELVE_MONTHS = OrganizerName.of("극단 열두달");
    private static final long TWELVE_MONTHS_ID = 12L;

    private static Organizer activeTwelveMonths() {
        return Organizer.restore(TWELVE_MONTHS_ID, TWELVE_MONTHS, OrganizerStatus.ACTIVE, OrganizerPublicProfile.empty());
    }

    @Nested
    @DisplayName("상태")
    class Status {

        @Test
        @DisplayName("심사 중으로 시작하고 공개 정보는 비어 있다 — 아직 저장 전이라 id가 없다")
        void startsUnderReview() {
            Organizer organizer = Organizer.underReview(TWELVE_MONTHS);

            assertThat(organizer.id()).isEmpty();
            assertThat(organizer.status()).isEqualTo(OrganizerStatus.UNDER_REVIEW);
            assertThat(organizer.isActive()).isFalse();
            assertThat(organizer.publicProfile()).isEqualTo(OrganizerPublicProfile.empty());
        }

        @Test
        @DisplayName("승인하면 활동이 된다")
        void approvalActivates() {
            Organizer organizer = Organizer.underReview(TWELVE_MONTHS);

            organizer.approve();

            assertThat(organizer.status()).isEqualTo(OrganizerStatus.ACTIVE);
            assertThat(organizer.isActive()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("심사 중이 아니면 승인할 수 없다")
        @EnumSource(value = OrganizerStatus.class, names = "UNDER_REVIEW", mode = EnumSource.Mode.EXCLUDE)
        void onlyUnderReviewCanBeApproved(OrganizerStatus status) {
            Organizer organizer = Organizer.restore(TWELVE_MONTHS_ID, TWELVE_MONTHS, status, OrganizerPublicProfile.empty());

            assertRejected(organizer::approve, OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW);
            assertThat(organizer.status()).isEqualTo(status);
        }

        @Test
        @DisplayName("닫힌 주최자도 지우지 않고 '닫힘'으로 되살린다 — 이름과 공개 정보가 그대로 남는다")
        void closedOrganizerRemains() {
            OrganizerPublicProfile profile = new OrganizerPublicProfile("2019년부터 활동한 소극장 극단",
                    List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater")), null);

            Organizer closed = Organizer.restore(TWELVE_MONTHS_ID, TWELVE_MONTHS, OrganizerStatus.CLOSED, profile);

            assertThat(closed.status()).isEqualTo(OrganizerStatus.CLOSED);
            assertThat(closed.name()).isEqualTo(TWELVE_MONTHS);
            assertThat(closed.publicProfile()).isEqualTo(profile);
            assertThat(closed.isActive()).isFalse();
        }

        @Test
        @DisplayName("저장하는 상태는 심사 중 · 활동 · 닫힘 · 영구 정지 넷뿐이다 — 정지 1~3단계는 상태가 아니다 (docs/14)")
        void storesFourStatuses() {
            assertThat(OrganizerStatus.values()).containsExactly(OrganizerStatus.UNDER_REVIEW, OrganizerStatus.ACTIVE,
                    OrganizerStatus.CLOSED, OrganizerStatus.PERMANENTLY_SUSPENDED);
        }
    }

    @Nested
    @DisplayName("주최자명 변경")
    class Rename {

        @Test
        @DisplayName("바로 바뀌고 언제·무엇에서 무엇으로·누가 바꿨는지 이력을 돌려준다")
        void renamesWithHistory() {
            Organizer organizer = activeTwelveMonths();
            OrganizerName newName = OrganizerName.of("극단 열두달 프로젝트");

            OrganizerNameChange change = organizer.rename(newName, JUNGHOON, REVIEWED_AT).orElseThrow();

            assertThat(organizer.name()).isEqualTo(newName);
            assertThat(change).isEqualTo(new OrganizerNameChange(TWELVE_MONTHS, newName, JUNGHOON, REVIEWED_AT));
        }

        @Test
        @DisplayName("지금 이름과 같으면 아무것도 바꾸지 않고 이력도 없다")
        void sameNameLeavesNoHistory() {
            Organizer organizer = activeTwelveMonths();

            assertThat(organizer.rename(OrganizerName.of(" 극단 열두달 "), JUNGHOON, REVIEWED_AT)).isEmpty();
            assertThat(organizer.name()).isEqualTo(TWELVE_MONTHS);
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("활동 중이 아닌 주최자의 이름은 바꿀 수 없다")
        @EnumSource(value = OrganizerStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        void onlyActiveCanBeRenamed(OrganizerStatus status) {
            Organizer organizer = Organizer.restore(TWELVE_MONTHS_ID, TWELVE_MONTHS, status, OrganizerPublicProfile.empty());

            assertRejected(() -> organizer.rename(OrganizerName.of("새 이름"), JUNGHOON, REVIEWED_AT),
                    OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
            assertThat(organizer.name()).isEqualTo(TWELVE_MONTHS);
        }

        @Test
        @DisplayName("이력은 바꾸기 전과 다른 이름이어야 한다")
        void historyNeedsDifferentNames() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new OrganizerNameChange(TWELVE_MONTHS, TWELVE_MONTHS, JUNGHOON, REVIEWED_AT));
        }
    }

    @Nested
    @DisplayName("공개 정보와 로고 기본 표시")
    class PublicProfile {

        @Test
        @DisplayName("소개 · SNS 링크 · 로고는 모두 선택이다 — 빈 소개는 없는 것으로 둔다")
        void everythingIsOptional() {
            OrganizerPublicProfile profile = new OrganizerPublicProfile("   ", null, null);

            assertThat(profile).isEqualTo(OrganizerPublicProfile.empty());
            assertThat(profile.introduction()).isNull();
            assertThat(profile.snsLinks()).isEmpty();
            assertThat(profile.hasLogo()).isFalse();
        }

        @Test
        @DisplayName("활동 중인 주최자의 공개 정보를 바꾼다")
        void changesPublicProfile() {
            Organizer organizer = activeTwelveMonths();
            OrganizerPublicProfile profile = new OrganizerPublicProfile(" 2019년부터 활동한 소극장 극단 ",
                    List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater")),
                    new OrganizerLogo("organizer-logo/12.png"));

            organizer.changePublicProfile(profile);

            assertThat(organizer.publicProfile().introduction()).isEqualTo("2019년부터 활동한 소극장 극단");
            assertThat(organizer.publicProfile().hasLogo()).isTrue();
        }

        @Test
        @DisplayName("활동 중이 아닌 주최자의 공개 정보는 바꿀 수 없다")
        void onlyActiveCanChangePublicProfile() {
            Organizer closed = Organizer.restore(TWELVE_MONTHS_ID, TWELVE_MONTHS, OrganizerStatus.CLOSED,
                    OrganizerPublicProfile.empty());

            assertRejected(() -> closed.changePublicProfile(new OrganizerPublicProfile("소개", List.of(), null)),
                    OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
        }

        @Test
        @DisplayName("로고가 없으면 이름 첫 글자 + 주최자 id로 정해진 색 — 극단 열두달 → '극'")
        void initialAvatarFromNameAndId() {
            Organizer organizer = activeTwelveMonths();

            assertThat(organizer.initialAvatar())
                    .isEqualTo(new InitialAvatar("극", InitialAvatar.paletteIndexOf(TWELVE_MONTHS_ID)));
        }

        @Test
        @DisplayName("이름을 바꾸면 첫 글자는 따라 바뀌고 색은 그대로다")
        void colorStaysAfterRename() {
            Organizer organizer = activeTwelveMonths();
            int colorBefore = organizer.initialAvatar().paletteIndex();

            organizer.rename(OrganizerName.of("열두달 프로젝트"), JUNGHOON, REVIEWED_AT);

            assertThat(organizer.initialAvatar().initial()).isEqualTo("열");
            assertThat(organizer.initialAvatar().paletteIndex()).isEqualTo(colorBefore);
        }

        @Test
        @DisplayName("저장 전 주최자는 색을 정할 id가 없다")
        void unsavedOrganizerHasNoColor() {
            assertThatIllegalStateException().isThrownBy(() -> Organizer.underReview(TWELVE_MONTHS).initialAvatar());
        }
    }
}
