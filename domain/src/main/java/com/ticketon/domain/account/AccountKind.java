package com.ticketon.domain.account;

import com.ticketon.domain.policy.TimePolicy;
import java.time.Duration;
import java.util.Optional;

/**
 * 계정 종류 — 계정 4종(docs/01 "계정 4종").
 * <p>
 * 종류마다 쓰는 기능이 겹치지 않아 서로의 영역에 들어가지 못한다. 주최자 쪽 로그인으로는 예매할 수 없고
 * (docs/01 "같은 이메일로 양쪽 가입"), 검표 계정은 검표 화면만 본다(docs/01 "검표 계정").
 * 계정 id는 종류마다 따로 매기므로 계정 하나는 종류와 id를 함께 봐야 정해진다({@link AccountRef}).
 */
public enum AccountKind {

    /** 사용자(관객) — 자유 가입. 예매·변경·취소·내 티켓. */
    AUDIENCE,

    /** 주최자 운영진(소유자·운영자) — 주최자 쪽 로그인. 소속 주최자의 행사 개설·운영. */
    STAFF,

    /** 플랫폼 관리자(관리자·최고 관리자) — 최고 관리자가 발급. */
    ADMIN,

    /** 검표 — 운영진이 행사별로 발급. 검표 화면만. */
    CHECKIN;

    /**
     * 마지막 요청 뒤 이만큼 요청이 없으면 로그아웃된다(docs/01 "로그인 유지와 링크 유효 시간"). 비어 있으면 비활성 만료가 없다 — 15시와 19시 30분 회차
     * 사이에 놓아 둔 검표 태블릿이 끊기면 안 된다. 검표 계정은 마지막 회차 종료 시 잠긴다(docs/01 "검표 계정").
     */
    public Optional<Duration> loginIdleTimeout() {
        return switch (this) {
            case AUDIENCE -> Optional.of(TimePolicy.AUDIENCE_LOGIN_IDLE_TIMEOUT);
            case STAFF, ADMIN -> Optional.of(TimePolicy.STAFF_AND_ADMIN_LOGIN_IDLE_TIMEOUT);
            case CHECKIN -> Optional.empty();
        };
    }

    /**
     * 브라우저를 닫아도 로그인이 남는지 — 관객과 검표 계정. 닫을 때 끊기면 비활성 만료를 길게 둔 뜻이 없다. 공용 PC는 로그아웃으로 끊는다(docs/01). 주최자 쪽 ·
     * 관리자는 돈과 개인정보를 다루므로 브라우저를 닫으면 끊는다.
     */
    public boolean keepsLoginAfterBrowserCloses() {
        return this == AUDIENCE || this == CHECKIN;
    }
}
