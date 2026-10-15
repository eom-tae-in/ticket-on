package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("관객 로그인 수단 — 카카오·구글 연결·해제")
class AudienceLoginMethodsTest {

    private static final long SEOYUN_ID = 7;

    private static final long OTHER_ACCOUNT_ID = 8;

    private static final Instant LINK_TIME = SIGNUP_TIME.plusSeconds(3600);

    private static final OAuthIdentity SEOYUN_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "3141592653");

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private static final OAuthIdentity ANOTHER_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "2718281828");

    @Nested
    @DisplayName("연결")
    class Link {

        @Test
        @DisplayName("연결하지 않은 제공자 계정을 연결하면 저장할 연결이 나오고, 연결된 제공자에 들어간다")
        void linksNewIdentity() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH),
                    List.of());

            Optional<AudienceOAuthLink> newLink = methods.link(SEOYUN_KAKAO, Optional.empty(), LINK_TIME);

            assertThat(newLink).contains(new AudienceOAuthLink(SEOYUN_ID, SEOYUN_KAKAO, LINK_TIME));
            assertThat(methods.linkedProviders()).containsExactly(OAuthProvider.KAKAO);
        }

        @Test
        @DisplayName("이용 정지 중에도 연결된다 — 정지로 막히는 것은 새 예매·변경·신고뿐이다 (docs/01)")
        void suspendedAccountLinks() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.SUSPENDED, PASSWORD_HASH),
                    List.of());

            assertThat(methods.link(SEOYUN_GOOGLE, Optional.empty(), LINK_TIME)).isPresent();
        }

        @Test
        @DisplayName("이미 이 계정에 연결된 제공자 계정이면 할 일이 없다")
        void alreadyLinkedToThisAccount() {
            AudienceOAuthLink existingLink = linkOf(SEOYUN_ID, SEOYUN_KAKAO);
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, null),
                    List.of(existingLink));

            assertThat(methods.link(SEOYUN_KAKAO, Optional.of(existingLink), LINK_TIME)).isEmpty();
            assertThat(methods.linkedProviders()).containsExactly(OAuthProvider.KAKAO);
        }

        @Test
        @DisplayName("다른 관객 계정에 연결된 제공자 계정은 연결하지 않는다 — 제공자 계정 하나는 관객 계정 하나에만")
        void identityLinkedToAnotherAccount() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH),
                    List.of());

            assertRejected(() -> methods.link(SEOYUN_KAKAO, Optional.of(linkOf(OTHER_ACCOUNT_ID, SEOYUN_KAKAO)), LINK_TIME),
                    AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED);
            assertThat(methods.linkedProviders()).isEmpty();
        }

        @Test
        @DisplayName("같은 제공자의 다른 계정이 연결돼 있으면 연결하지 않는다 — 제공자마다 하나씩만")
        void providerAlreadyLinked() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO)));

            assertRejected(() -> methods.link(ANOTHER_KAKAO, Optional.empty(), LINK_TIME),
                    AccountErrorCode.OAUTH_PROVIDER_ALREADY_LINKED);
        }

        @Test
        @DisplayName("카카오가 연결돼 있어도 구글은 따로 연결된다")
        void otherProviderLinks() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, null),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO)));

            methods.link(SEOYUN_GOOGLE, Optional.empty(), LINK_TIME);

            assertThat(methods.linkedProviders()).containsExactly(OAuthProvider.KAKAO, OAuthProvider.GOOGLE);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AudienceAccountStatus.class, names = {"PENDING_VERIFICATION", "WITHDRAWN"})
        @DisplayName("인증 대기·탈퇴 계정은 연결할 수 없다")
        void notManageable(AudienceAccountStatus status) {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(status, PASSWORD_HASH), List.of());

            assertRejected(() -> methods.link(SEOYUN_KAKAO, Optional.empty(), LINK_TIME),
                    AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        }
    }

    @Nested
    @DisplayName("해제 — 로그인 수단이 하나도 남지 않게 하는 해제는 막는다")
    class Unlink {

        @Test
        @DisplayName("비밀번호가 있으면 하나뿐인 연결도 해제된다 — 비밀번호로 로그인할 수 있다")
        void passwordRemains() {
            AudienceOAuthLink kakaoLink = linkOf(SEOYUN_ID, SEOYUN_KAKAO);
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH),
                    List.of(kakaoLink));

            assertThat(methods.unlink(OAuthProvider.KAKAO)).isEqualTo(kakaoLink);
            assertThat(methods.linkedProviders()).isEmpty();
            assertThat(methods.isPasswordSet()).isTrue();
        }

        @Test
        @DisplayName("비밀번호가 없어도 다른 연결이 남으면 해제된다")
        void anotherLinkRemains() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, null),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO), linkOf(SEOYUN_ID, SEOYUN_GOOGLE)));

            methods.unlink(OAuthProvider.GOOGLE);

            assertThat(methods.linkedProviders()).containsExactly(OAuthProvider.KAKAO);
        }

        @Test
        @DisplayName("비밀번호 없이 연결이 하나뿐이면 막는다 — 비밀번호 찾기로 비밀번호를 정하거나 다른 계정을 연결한 뒤 해제한다")
        void lastLoginMethod() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, null),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO)));

            assertRejected(() -> methods.unlink(OAuthProvider.KAKAO), AccountErrorCode.LAST_LOGIN_METHOD);
            assertThat(methods.linkedProviders()).containsExactly(OAuthProvider.KAKAO);
        }

        @Test
        @DisplayName("둘 다 연결된 비밀번호 없는 계정은 하나를 해제한 뒤 나머지 하나는 해제할 수 없다")
        void secondUnlinkIsTheLast() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, null),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO), linkOf(SEOYUN_ID, SEOYUN_GOOGLE)));

            methods.unlink(OAuthProvider.KAKAO);

            assertRejected(() -> methods.unlink(OAuthProvider.GOOGLE), AccountErrorCode.LAST_LOGIN_METHOD);
        }

        @Test
        @DisplayName("연결하지 않은 제공자는 해제할 수 없다")
        void notLinked() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO)));

            assertRejected(() -> methods.unlink(OAuthProvider.GOOGLE), AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED);
        }

        @Test
        @DisplayName("이용 정지 중에도 해제된다")
        void suspendedAccountUnlinks() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.SUSPENDED, PASSWORD_HASH),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO)));

            methods.unlink(OAuthProvider.KAKAO);

            assertThat(methods.linkedProviders()).isEmpty();
        }

        @Test
        @DisplayName("탈퇴한 계정은 해제할 수 없다")
        void withdrawnAccount() {
            AudienceLoginMethods methods = AudienceLoginMethods.of(account(AudienceAccountStatus.WITHDRAWN, PASSWORD_HASH),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO)));

            assertRejected(() -> methods.unlink(OAuthProvider.KAKAO), AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        }
    }

    @Nested
    @DisplayName("만들 때")
    class Creation {

        @Test
        @DisplayName("다른 계정의 연결이 섞이면 거부한다")
        void rejectsForeignLink() {
            assertThatIllegalArgumentException().isThrownBy(() -> AudienceLoginMethods.of(
                    account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH), List.of(linkOf(OTHER_ACCOUNT_ID, SEOYUN_KAKAO))));
        }

        @Test
        @DisplayName("같은 제공자의 연결이 둘이면 거부한다")
        void rejectsDuplicateProvider() {
            assertThatIllegalArgumentException().isThrownBy(() -> AudienceLoginMethods.of(
                    account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH),
                    List.of(linkOf(SEOYUN_ID, SEOYUN_KAKAO), linkOf(SEOYUN_ID, ANOTHER_KAKAO))));
        }

        @Test
        @DisplayName("저장하지 않은 계정은 거부한다")
        void rejectsUnsavedAccount() {
            assertThatIllegalArgumentException().isThrownBy(() -> AudienceLoginMethods.of(
                    AudienceAccountFixtures.activeAccount(), List.of()));
        }

        @Test
        @DisplayName("비밀번호를 정했는지 계정에서 읽는다")
        void readsPasswordFromAccount() {
            assertThat(AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, PASSWORD_HASH), List.of())
                    .isPasswordSet()).isTrue();
            assertThat(AudienceLoginMethods.of(account(AudienceAccountStatus.ACTIVE, null), List.of())
                    .isPasswordSet()).isFalse();
        }
    }

    private static AudienceOAuthLink linkOf(long accountId, OAuthIdentity identity) {
        return new AudienceOAuthLink(accountId, identity, SIGNUP_TIME);
    }

    /**
     * 저장소에서 읽은 서윤의 계정.
     *
     * @param passwordHash 비밀번호 해시. 카카오·구글로만 가입해 비밀번호가 없으면 null.
     */
    private static AudienceAccount account(AudienceAccountStatus status, PasswordHash passwordHash) {
        Instant emailVerifiedAt = status == AudienceAccountStatus.PENDING_VERIFICATION ? null : SIGNUP_TIME;
        List<AudienceSuspension> suspensions = status == AudienceAccountStatus.SUSPENDED
                ? List.of(AudienceSuspension.startingAt("번호를 바꿔가며 1인 매수 제한을 넘는 예매", SIGNUP_TIME.plusSeconds(60)))
                : List.of();
        return AudienceAccount.restore(SEOYUN_ID, adultPersonalInformation(), passwordHash, SIGNUP_TIME,
                SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNUP_TIME), status, emailVerifiedAt,
                suspensions);
    }
}
