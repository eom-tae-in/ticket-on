package com.ticketon.api.account;

import com.ticketon.domain.error.ErrorCode;

/**
 * 카카오·구글 로그인 흐름의 오류 코드 — 제공자와 주고받다 실패했거나 세션에 진행 중인 로그인이 없을 때. 계정 규칙에 걸린 것은 계정 도메인의
 * 오류 코드(AccountErrorCode)를 쓴다. 문구는 화면에 그대로 나간다.
 */
public enum OAuthLoginErrorCode implements ErrorCode {

    /**
     * 제공자에서 로그인을 마치지 못했다 — 관객이 동의를 취소했거나, 제공자가 오류를 돌려줬거나, 로그인 시작과 돌아온 요청이 맞지 않는다(state 불일치·
     * 세션 만료 등). 까닭은 로그에만 남긴다.
     */
    OAUTH_LOGIN_FAILED("로그인을 마치지 못했습니다. 다시 시도해 주세요."),

    /** 세션에 진행 중인 카카오·구글 로그인(결과·추가 정보 입력 대기)이 없다 — 세션이 끝났거나 로그인을 시작하지 않았다. */
    OAUTH_LOGIN_NOT_IN_PROGRESS("진행 중인 카카오·구글 로그인이 없습니다. 처음부터 다시 로그인해 주세요.");

    private final String message;

    OAuthLoginErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
