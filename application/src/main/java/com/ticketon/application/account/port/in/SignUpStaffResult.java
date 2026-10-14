package com.ticketon.application.account.port.in;

/**
 * 운영진 로그인 가입 결과.
 *
 * @param staffAccountId 새 운영진 로그인 계정 id
 * @param email          인증 링크를 보낸 이메일 주소(적은 그대로)
 */
public record SignUpStaffResult(long staffAccountId, String email) {
}
