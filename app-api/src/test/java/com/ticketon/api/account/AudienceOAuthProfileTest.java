package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("제공자가 알려 준 관객 정보")
class AudienceOAuthProfileTest {

    @Nested
    @DisplayName("구글 — sub, name, email, email_verified")
    class Google {

        @Test
        @DisplayName("구글이 확인한 이메일은 확인된 이메일이고, 이름은 미리 채울 값이다")
        void verifiedEmail() {
            AudienceOAuthProfile profile = AudienceOAuthProfile.from(OAuthProviderCode.GOOGLE,
                    Map.of("sub", "109876543210", "name", " 한서윤 ", "email", "seoyun@gmail.com", "email_verified", true));

            assertThat(profile.identity()).isEqualTo(new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210"));
            assertThat(profile.name()).isEqualTo("한서윤");
            assertThat(profile.verifiedEmail()).contains(EmailAddress.of("seoyun@gmail.com"));
        }

        @Test
        @DisplayName("email_verified가 참이 아니면 이메일은 미리 채울 값일 뿐 확인된 이메일이 아니다")
        void unverifiedEmail() {
            AudienceOAuthProfile profile = AudienceOAuthProfile.from(OAuthProviderCode.GOOGLE,
                    Map.of("sub", "109876543210", "email", "seoyun@gmail.com", "email_verified", false));

            assertThat(profile.email()).isEqualTo(EmailAddress.of("seoyun@gmail.com"));
            assertThat(profile.verifiedEmail()).isEmpty();
        }

        @Test
        @DisplayName("우리 이메일 모양 규칙에 맞지 않는 이메일은 받지 않은 것으로 본다")
        void malformedEmail() {
            AudienceOAuthProfile profile = AudienceOAuthProfile.from(OAuthProviderCode.GOOGLE,
                    Map.of("sub", "109876543210", "email", "not an email", "email_verified", true));

            assertThat(profile.email()).isNull();
            assertThat(profile.verifiedEmail()).isEmpty();
        }

        @Test
        @DisplayName("사용자 id가 없으면 거부한다")
        void missingUserId() {
            assertThatIllegalArgumentException().isThrownBy(() -> AudienceOAuthProfile.from(OAuthProviderCode.GOOGLE,
                    Map.of("email", "seoyun@gmail.com")));
        }
    }

    @Nested
    @DisplayName("카카오 — id, kakao_account")
    class Kakao {

        @Test
        @DisplayName("회원번호(숫자)를 사용자 id로 쓰고, 유효하고 인증된 이메일만 확인된 이메일이다")
        void verifiedEmail() {
            AudienceOAuthProfile profile = AudienceOAuthProfile.from(OAuthProviderCode.KAKAO, Map.of("id", 3141592653L,
                    "kakao_account", kakaoAccount("seoyun@kakao.com", true, true)));

            assertThat(profile.identity()).isEqualTo(new OAuthIdentity(OAuthProvider.KAKAO, "3141592653"));
            assertThat(profile.verifiedEmail()).contains(EmailAddress.of("seoyun@kakao.com"));
        }

        @Test
        @DisplayName("인증되지 않았거나 유효하지 않은 이메일은 확인된 이메일이 아니다")
        void unverifiedOrInvalidEmail() {
            AudienceOAuthProfile unverified = AudienceOAuthProfile.from(OAuthProviderCode.KAKAO, Map.of("id", 1L,
                    "kakao_account", kakaoAccount("seoyun@kakao.com", true, false)));
            AudienceOAuthProfile invalid = AudienceOAuthProfile.from(OAuthProviderCode.KAKAO, Map.of("id", 1L,
                    "kakao_account", kakaoAccount("seoyun@kakao.com", false, true)));

            assertThat(unverified.verifiedEmail()).isEmpty();
            assertThat(invalid.verifiedEmail()).isEmpty();
        }

        @Test
        @DisplayName("별명(profile.nickname)은 실명이 아니라 이름으로 쓰지 않고, 실명(name)을 받았을 때만 미리 채운다")
        void nameIsNotNickname() {
            Map<String, Object> accountWithNickname = kakaoAccount(null, false, false);
            accountWithNickname.put("profile", Map.of("nickname", "서윤이"));
            Map<String, Object> accountWithName = kakaoAccount(null, false, false);
            accountWithName.put("name", "한서윤");

            assertThat(AudienceOAuthProfile.from(OAuthProviderCode.KAKAO, Map.of("id", 1L, "kakao_account", accountWithNickname))
                    .name()).isNull();
            assertThat(AudienceOAuthProfile.from(OAuthProviderCode.KAKAO, Map.of("id", 1L, "kakao_account", accountWithName))
                    .name()).isEqualTo("한서윤");
        }

        @Test
        @DisplayName("이메일 동의를 받지 못해 kakao_account가 없어도 사용자 id만으로 만든다 — 이메일은 추가 정보 입력에서 받는다")
        void withoutKakaoAccount() {
            AudienceOAuthProfile profile = AudienceOAuthProfile.from(OAuthProviderCode.KAKAO, Map.of("id", 1L));

            assertThat(profile.email()).isNull();
            assertThat(profile.name()).isNull();
        }

        private static Map<String, Object> kakaoAccount(String email, boolean valid, boolean verified) {
            Map<String, Object> account = new HashMap<>();
            if (email != null) {
                account.put("email", email);
            }
            account.put("is_email_valid", valid);
            account.put("is_email_verified", verified);
            return account;
        }
    }
}
