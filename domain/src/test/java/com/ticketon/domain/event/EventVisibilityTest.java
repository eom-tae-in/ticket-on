package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("행사 공개 수준 3단계 (docs/03 \"공개 수준 3단계\")")
class EventVisibilityTest {

    private static final EventPasswordHash HASH_OF_0315 = new EventPasswordHash("{test}0315");
    private static final EventPasswordHash HASH_OF_1225 = new EventPasswordHash("{test}1225");

    @Nested
    @DisplayName("세 단계")
    class ThreeLevels {

        @ParameterizedTest(name = "{0} — 목록 · 검색 {1}, 암호 {2}")
        @DisplayName("공개만 목록 · 검색에 뜨고, 암호 보호만 암호가 필요하다")
        @CsvSource({
                "PUBLIC, true, false",
                "LINK_ONLY, false, false",
                "PASSWORD_PROTECTED, false, true"
        })
        void listedAndPassword(EventVisibility visibility, boolean listed, boolean requiresPassword) {
            assertThat(visibility.isListed()).isEqualTo(listed);
            assertThat(visibility.requiresPassword()).isEqualTo(requiresPassword);
        }

        @Test
        @DisplayName("처음에는 공개다 — 암호가 없다")
        void publicByDefault() {
            EventVisibilitySetting setting = EventVisibilitySetting.publicByDefault();

            assertThat(setting.visibility()).isEqualTo(EventVisibility.PUBLIC);
            assertThat(setting.passwordHash()).isNull();
            assertThat(setting.isListed()).isTrue();
            assertThat(setting.requiresPassword()).isFalse();
        }

        @Test
        @DisplayName("암호 보호일 때만 암호 해시를 가진다")
        void passwordHashOnlyWhenProtected() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, null));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new EventVisibilitySetting(EventVisibility.LINK_ONLY, HASH_OF_0315));
            assertThat(new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315).requiresPassword()).isTrue();
        }
    }

    @Nested
    @DisplayName("바꾸기")
    class Change {

        @Test
        @DisplayName("암호 보호로 바꾸면서 암호를 정한다 — 목록에서 사라지고 암호가 걸린다")
        void toPasswordProtectedWithPassword() {
            EventVisibilitySetting changed = EventVisibilitySetting.publicByDefault()
                    .change(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315);

            assertThat(changed).isEqualTo(new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315));
            assertThat(changed.isListed()).isFalse();
        }

        @ParameterizedTest(name = "지금 {0}")
        @DisplayName("암호 보호인데 암호가 없으면 막는다 — 걸려 있던 암호도 없을 때")
        @EnumSource(value = EventVisibility.class, names = {"PUBLIC", "LINK_ONLY"})
        void rejectsPasswordProtectedWithoutPassword(EventVisibility current) {
            EventVisibilitySetting setting = EventVisibilitySetting.publicByDefault().change(current, null);

            assertRejected(() -> setting.change(EventVisibility.PASSWORD_PROTECTED, null), EventErrorCode.EVENT_PASSWORD_REQUIRED);
        }

        @Test
        @DisplayName("이미 암호 보호면 암호를 새로 적지 않아도 걸려 있던 암호를 그대로 쓴다 — 다른 설정을 고치며 같은 단계를 다시 저장할 때")
        void keepsCurrentPasswordWhenStillProtected() {
            EventVisibilitySetting protectedSetting = new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315);

            assertThat(protectedSetting.change(EventVisibility.PASSWORD_PROTECTED, null).passwordHash()).isEqualTo(HASH_OF_0315);
        }

        @Test
        @DisplayName("암호를 새로 적으면 새 암호로 바뀐다")
        void replacesPassword() {
            EventVisibilitySetting protectedSetting = new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315);

            assertThat(protectedSetting.change(EventVisibility.PASSWORD_PROTECTED, HASH_OF_1225).passwordHash())
                    .isEqualTo(HASH_OF_1225);
        }

        @ParameterizedTest(name = "암호 보호 → {0}")
        @DisplayName("공개 · 링크 전용으로 바꾸면 걸려 있던 암호를 버린다 — 다시 암호 보호로 두려면 암호를 새로 정한다")
        @EnumSource(value = EventVisibility.class, names = {"PUBLIC", "LINK_ONLY"})
        void dropsPasswordWhenNoLongerProtected(EventVisibility newVisibility) {
            EventVisibilitySetting protectedSetting = new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315);

            EventVisibilitySetting changed = protectedSetting.change(newVisibility, null);

            assertThat(changed.passwordHash()).isNull();
            assertRejected(() -> changed.change(EventVisibility.PASSWORD_PROTECTED, null), EventErrorCode.EVENT_PASSWORD_REQUIRED);
        }

        @Test
        @DisplayName("링크 전용 ↔ 공개는 암호 없이 오간다 — 공개로 바꾸면 그때부터 목록에 뜬다")
        void betweenPublicAndLinkOnly() {
            EventVisibilitySetting linkOnly = EventVisibilitySetting.publicByDefault().change(EventVisibility.LINK_ONLY, null);

            assertThat(linkOnly.isListed()).isFalse();
            assertThat(linkOnly.change(EventVisibility.PUBLIC, null).isListed()).isTrue();
        }

        @Test
        @DisplayName("암호 보호가 아닌데 암호 해시를 넘기면 받지 않는다 — 해시는 암호 보호일 때만 만든다")
        void rejectsPasswordHashForUnprotected() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> EventVisibilitySetting.publicByDefault().change(EventVisibility.LINK_ONLY, HASH_OF_0315));
        }
    }

    @Nested
    @DisplayName("암호와 해시")
    class PasswordAndHash {

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("비었거나 공백뿐인 암호는 암호가 아니다")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void blankIsNotAPassword(String input) {
            assertThat(EventPassword.isValid(input)).isFalse();
            if (input != null) {
                assertThatIllegalArgumentException().isThrownBy(() -> new EventPassword(input));
            }
        }

        @Test
        @DisplayName("짧은 암호도 받고, 적은 그대로 쓴다 — 최소 길이는 문서에 없다")
        void shortPasswordAccepted() {
            assertThat(EventPassword.isValid("0315")).isTrue();
            assertThat(new EventPassword(" 0315 ").value()).isEqualTo(" 0315 ");
        }

        @Test
        @DisplayName("원문과 해시는 문자열로 바꾸면 값을 가린다")
        void masked() {
            assertThat(new EventPassword("0315")).hasToString("EventPassword[****]");
            assertThat(HASH_OF_0315).hasToString("EventPasswordHash[****]");
            assertThat(new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, HASH_OF_0315).toString())
                    .doesNotContain("0315");
            assertThatIllegalArgumentException().isThrownBy(() -> new EventPasswordHash(" "));
        }
    }
}
