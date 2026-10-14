package com.ticketon.api.error;

import com.ticketon.domain.error.ErrorCode;

/**
 * 도메인 오류 코드가 없는 오류에 쓰는 API 공통 코드 — 입력 형식, 로그인, 없는 주소, 처리하지 못한 오류.
 * <p>
 * 규칙 위반처럼 도메인이 아는 오류는 각 도메인의 오류 코드를 쓴다. 여기에 도메인 사정을 담지 않는다.
 */
public enum ApiErrorCode implements ErrorCode {

    /** 400 — 입력 검증 실패, 읽을 수 없는 본문, 형식이 틀린 값. 칸별 사유는 fieldErrors에 담는다. */
    INVALID_INPUT("입력한 내용을 다시 확인해 주세요."),

    /** 401 — 로그인이 필요한데 로그인 정보가 없다. */
    UNAUTHENTICATED("로그인이 필요합니다."),

    /** 403 — 도메인 오류 코드 없이 막힌 요청(보안 설정 등). */
    ACCESS_DENIED("이 요청을 처리할 권한이 없습니다."),

    /** 404 — 없는 주소. 없는 행사·티켓 같은 대상은 각 도메인의 오류 코드를 쓴다. */
    NOT_FOUND("요청한 주소를 찾을 수 없습니다."),

    /** 그 밖의 4xx — 지원하지 않는 메서드·형식 등 화면이 정상적으로는 보내지 않는 요청. */
    UNSUPPORTED_REQUEST("지원하지 않는 요청입니다."),

    /** 500 — 처리하지 못한 오류. 내부 내용은 로그에만 남기고 화면에는 이 문구만 보낸다. */
    INTERNAL_ERROR("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final String message;

    ApiErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
