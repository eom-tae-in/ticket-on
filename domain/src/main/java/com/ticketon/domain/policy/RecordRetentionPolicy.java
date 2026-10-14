package com.ticketon.domain.policy;

import java.time.Period;

/**
 * docs/99 C "보관 기간" 표의 확정 숫자.
 * <p>
 * "행사 종료 후"로 세는 기간은 행사가 끝난 날부터 센다(docs/03, docs/10). 바꾼 날이나 만든 날부터 세지 않는다.
 */
public final class RecordRetentionPolicy {

    /** 제목·소개·포스터·회차 출연 변경 이력 — 행사 종료 후 6개월(전자상거래법 표시·광고 기록). docs/03 "보관은 행사가 끝난 날부터 6개월", docs/11 "데이터 보관" */
    public static final Period CHANGE_HISTORY_RETENTION_AFTER_EVENT_END = Period.ofMonths(6);

    /** 입장 기록·티켓 없는 입장 기록 — 행사 종료 30일 후 통째로 삭제. 집계 숫자만 남긴다. docs/10 "검표 기록 보관", docs/11 "데이터 보관" */
    public static final Period ENTRY_RECORD_RETENTION_AFTER_EVENT_END = Period.ofDays(30);

    /** 부가 기록(알림 발송 이력, 접속 기록) — 90일 후 파기. docs/11 "데이터 보관" */
    public static final Period AUXILIARY_RECORD_RETENTION = Period.ofDays(90);

    /**
     * 예매 건·티켓·결제 기록 법정 보존 — 계약·청약철회 5년, 대금결제·재화 공급 5년(전자상거래법 시행령 제6조).
     * 지나면 관객 개인 식별 정보만 지우고 집계는 남긴다. docs/11 "법정 보존 기간", docs/07
     */
    public static final Period LEGAL_TRANSACTION_RECORD_RETENTION = Period.ofYears(5);

    /** 소비자 불만·분쟁처리 기록 법정 보존 — 3년(전자상거래법 시행령 제6조). docs/11 "법정 보존 기간" */
    public static final Period LEGAL_DISPUTE_RECORD_RETENTION = Period.ofYears(3);

    private RecordRetentionPolicy() {
    }
}
