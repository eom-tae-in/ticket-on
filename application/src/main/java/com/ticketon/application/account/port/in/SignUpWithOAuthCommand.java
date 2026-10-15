package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.SignupConsentChoices;
import java.util.Objects;

/**
 * 관객 카카오·구글 가입 입력.
 *
 * @param identity                제공자 계정 — 로그인할 때 세션에 둔 '추가 정보 입력 대기'의 제공자 계정
 * @param personalInformation     가입 정보 넷 — 이름·생년월일·전화번호는 추가 정보 입력에서 적은 값, 이메일은 제공자가 확인한 이메일
 *                                또는 추가 정보 입력에서 적은 값
 * @param emailVerifiedByProvider 가입 정보의 이메일이 제공자가 확인한 이메일인지. 참이면 이메일 인증을 거친 것으로 본다.
 * @param consentChoices          추가 정보 입력에서 체크한 동의 항목
 */
public record SignUpWithOAuthCommand(OAuthIdentity identity, AudiencePersonalInformation personalInformation,
        boolean emailVerifiedByProvider, SignupConsentChoices consentChoices) {

    public SignUpWithOAuthCommand {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(consentChoices, "consentChoices");
    }
}
