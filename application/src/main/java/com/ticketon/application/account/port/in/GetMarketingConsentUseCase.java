package com.ticketon.application.account.port.in;

/**
 * 마케팅 수신 상태 조회 — 들어오는 포트. 마이페이지 알림 설정의 "마케팅 수신" 칸이 쓴다(docs/12 "알림 설정").
 * <p>
 * 지금 상태는 동의 기록의 가장 최근 줄이 정한다. 동의한 상태면 그 동의 일시도 돌려준다.
 */
public interface GetMarketingConsentUseCase {

    MarketingConsentResult getConsent(GetMarketingConsentQuery query);
}
