package com.ticketon.application.account.port.in;

/**
 * 관객 직접 가입 결과.
 *
 * @param audienceAccountId 새 관객 계정 id
 * @param email             인증 링크를 보낸 이메일 주소(적은 그대로)
 */
public record SignUpAudienceResult(long audienceAccountId, String email) {
}
