package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 마케팅 수신 동의·철회 — 들어오는 포트(docs/01 "약관과 동의 — 가입 시", docs/12 "마케팅 수신은 구분해서 표시", "마케팅 발송").
 * <p>
 * 가입 때 고른 마케팅 수신을 언제든 바꾼다. 바뀌면 동의 기록에 새 일시로 한 줄을 더하고(이전 기록은 그대로), 처리 결과를 그 관객에게 바로
 * 이메일로 알린다 — 법이 정한 14일 안의 처리 결과 안내다. 이 안내에는 광고를 넣지 않는다(docs/99 A-6). 지금 상태와 같으면 아무것도 더하지
 * 않고 안내도 보내지 않는다. 이용 정지 중에도 바꿀 수 있다.
 */
public interface ChangeMarketingConsentUseCase {

    /**
     * @return 바꾼 뒤의 마케팅 수신 상태
     * @throws RuleViolationException 인증 대기·탈퇴 계정(AUDIENCE_NOT_ACTIVE) — 로그인할 수 없으므로 보통은 생기지 않는다
     */
    MarketingConsentResult changeConsent(ChangeMarketingConsentCommand command);
}
