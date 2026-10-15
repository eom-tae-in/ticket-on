package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 관객 카카오·구글 로그인 결과 — 로그인됐거나, 추가 정보를 받아 가입해야 한다.
 */
public sealed interface LogInWithOAuthResult {

    /**
     * 연결된 관객 계정으로 로그인한다.
     *
     * @param account 로그인할 주체 — 관객(AUDIENCE) 계정. 들어오는 어댑터가 이 주체로 세션을 연다.
     */
    record LoggedIn(AccountRef account) implements LogInWithOAuthResult {

        public LoggedIn {
            Objects.requireNonNull(account, "account");
            if (account.kind() != AccountKind.AUDIENCE) {
                throw new IllegalArgumentException("관객 로그인 결과는 관객 계정이어야 합니다: " + account.principalName());
            }
        }
    }

    /**
     * 연결된 관객 계정이 없다 — 추가 정보 입력을 거쳐 가입한다(docs/01 "가입 경로 — OAuth", docs/13 "추가 정보 입력"). 들어오는 어댑터가
     * 제공자 계정을 '추가 정보 입력 대기'로 세션에 두고 추가 정보 화면으로 보낸다.
     */
    record SignUpRequired() implements LogInWithOAuthResult {
    }
}
