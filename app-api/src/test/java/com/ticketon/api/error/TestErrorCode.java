package com.ticketon.api.error;

import com.ticketon.domain.error.ErrorCode;

/** 시험용 도메인 오류 코드. 문서가 정해 둔 문구를 그대로 쓴다. */
enum TestErrorCode implements ErrorCode {

    /** docs/06 "두 사람이 동시에 같은 자리를 눌렀다면" */
    SEAT_ALREADY_TAKEN("방금 다른 분이 선택했습니다"),

    /** docs/05 "잘못된 코드" */
    COUPON_NOT_APPLICABLE("이 행사에서 사용할 수 없는 코드입니다"),

    /** docs/01 "번호 하나에 계정 하나" */
    PHONE_ALREADY_REGISTERED("이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요"),

    /** docs/01 "권한 두 가지" — 회차 취소는 소유자만 */
    OWNER_ONLY("소유자만 할 수 있습니다"),

    TICKET_NOT_FOUND("티켓을 찾을 수 없습니다");

    private final String message;

    TestErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
