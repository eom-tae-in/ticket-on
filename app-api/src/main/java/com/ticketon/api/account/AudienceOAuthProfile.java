package com.ticketon.api.account;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.OAuthIdentity;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 제공자가 알려 준 관객 정보 — 제공자 계정, 이름·이메일(있으면), 이메일을 제공자가 확인했는지.
 * <p>
 * 제공자마다 사용자 정보의 모양이 달라 여기서 한 모양으로 바꾼다. 구글은 이름과 이메일만 주고 생년월일·전화번호는 주지 않는다
 * (docs/01 "가입 경로 — OAuth"). 그래서 거의 모든 OAuth 가입자가 추가 정보 입력을 거친다.
 * <ul>
 *   <li>구글(사용자 정보 주소 v3): {@code sub}, {@code name}, {@code email}, {@code email_verified}</li>
 *   <li>카카오(사용자 정보 주소 v2): {@code id}, {@code kakao_account.email}, {@code kakao_account.is_email_valid},
 *       {@code kakao_account.is_email_verified}, {@code kakao_account.name}. 카카오 별명(profile.nickname)은 실명이 아니어서 이름으로 쓰지
 *       않는다 — 이름은 검표 수동 검색에 쓰인다(docs/01 "수집 정보").</li>
 * </ul>
 * 제공자가 확인했다고 한 이메일만 확인된 이메일로 본다. 카카오는 유효하고(is_email_valid) 인증된(is_email_verified) 이메일이어야 한다.
 * 우리 이메일 모양 규칙({@link EmailAddress#isValid})에 맞지 않는 이메일은 받지 않은 것으로 본다 — 관객이 추가 정보 입력에서 다시 적는다.
 *
 * @param provider      제공자
 * @param identity      제공자 계정
 * @param name          이름. 주지 않았거나 비었으면 null — 추가 정보 입력의 이름 칸을 미리 채우는 데만 쓴다.
 * @param email         이메일. 주지 않았거나 모양이 맞지 않으면 null.
 * @param emailVerified 이메일을 제공자가 확인했는지. 이메일이 없으면 거짓.
 */
record AudienceOAuthProfile(OAuthProviderCode provider, OAuthIdentity identity, @Nullable String name,
        @Nullable EmailAddress email, boolean emailVerified) {

    private static final String GOOGLE_USER_ID = "sub";

    private static final String KAKAO_USER_ID = "id";

    AudienceOAuthProfile {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(identity, "identity");
        if (email == null && emailVerified) {
            throw new IllegalArgumentException("이메일 없이 확인된 이메일일 수 없습니다");
        }
    }

    /** 사용자 정보에서 제공자 사용자 id가 든 칸 이름 — 구글 sub, 카카오 id(회원번호). */
    static String userIdAttributeOf(OAuthProviderCode provider) {
        return switch (provider) {
            case KAKAO -> KAKAO_USER_ID;
            case GOOGLE -> GOOGLE_USER_ID;
        };
    }

    /**
     * 제공자의 사용자 정보로 만든다.
     *
     * @throws IllegalArgumentException 사용자 id가 없거나 비었거나 너무 길면 — 제공자가 이상한 응답을 줬다
     */
    static AudienceOAuthProfile from(OAuthProviderCode provider, Map<String, Object> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return switch (provider) {
            case GOOGLE -> fromGoogle(attributes);
            case KAKAO -> fromKakao(attributes);
        };
    }

    /** 제공자가 확인한 이메일. 없거나 확인되지 않았으면 비어 있다. */
    Optional<EmailAddress> verifiedEmail() {
        return emailVerified ? Optional.ofNullable(email) : Optional.empty();
    }

    private static AudienceOAuthProfile fromGoogle(Map<String, Object> attributes) {
        EmailAddress email = emailOf(attributes.get("email"));
        boolean emailVerified = email != null && Boolean.TRUE.equals(attributes.get("email_verified"));
        return new AudienceOAuthProfile(OAuthProviderCode.GOOGLE, identityOf(OAuthProviderCode.GOOGLE,
                attributes.get(GOOGLE_USER_ID)), textOf(attributes.get("name")), email, emailVerified);
    }

    private static AudienceOAuthProfile fromKakao(Map<String, Object> attributes) {
        Map<?, ?> kakaoAccount = attributes.get("kakao_account") instanceof Map<?, ?> account ? account : Map.of();
        EmailAddress email = emailOf(kakaoAccount.get("email"));
        boolean emailVerified = email != null && Boolean.TRUE.equals(kakaoAccount.get("is_email_valid"))
                && Boolean.TRUE.equals(kakaoAccount.get("is_email_verified"));
        return new AudienceOAuthProfile(OAuthProviderCode.KAKAO, identityOf(OAuthProviderCode.KAKAO,
                attributes.get(KAKAO_USER_ID)), textOf(kakaoAccount.get("name")), email, emailVerified);
    }

    private static OAuthIdentity identityOf(OAuthProviderCode provider, @Nullable Object userId) {
        if (userId == null) {
            throw new IllegalArgumentException(provider + " 사용자 정보에 사용자 id가 없습니다");
        }
        return new OAuthIdentity(provider.toProvider(), userId.toString());
    }

    private static @Nullable EmailAddress emailOf(@Nullable Object value) {
        return value instanceof String text && EmailAddress.isValid(text) ? EmailAddress.of(text) : null;
    }

    private static @Nullable String textOf(@Nullable Object value) {
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }
}
