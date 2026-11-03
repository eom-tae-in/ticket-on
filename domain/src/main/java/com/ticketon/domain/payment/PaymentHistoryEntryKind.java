package com.ticketon.domain.payment;

/**
 * 결제·환불 내역 한 줄의 종류 — 결제 / 환불 / 배상 / 지연 환급(docs/08 "결제·환불 내역").
 * <table>
 *   <caption>종류와 실 결제액</caption>
 *   <tr><th>종류</th><th>언제</th><th>실 결제액</th></tr>
 *   <tr><td>결제</td><td>예매, 변경의 새 티켓</td><td>더한다</td></tr>
 *   <tr><td>환불</td><td>취소, 변경의 기존 티켓, 묶음권 그날 몫</td><td>뺀다</td></tr>
 *   <tr><td>지연 환급</td><td>주최자 사정으로 30분 이상 늦은 시작 — 입장한 티켓에 입장료의 10%</td><td><b>뺀다</b> — 입장료 일부를 돌려준 것이다</td></tr>
 *   <tr><td>배상</td><td>주최자 사정 취소 · 공연 중단 · 출연자 변경 — 입장료의 10%</td><td><b>빼지 않는다</b> — 입장료 환급이 아니다</td></tr>
 * </table>
 * 지연 환급은 실 결제액에서 환불과 똑같이 빠지지만 따로 둔다 — 지연 환급은 주최자 부담이라 정산에서 환불과 갈라 뺀다(docs/11 "판매 집계" — 환불액 ·
 * 지연 환급액이 따로 있다). 배상도 주최자 부담이고 정산에서 빠지지만, 관객이 낸 입장료를 돌려준 돈이 아니라 그 위에 더 주는 돈이다.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum PaymentHistoryEntryKind {

    /** 결제 — 예매 결제, 변경의 새 티켓 결제. */
    PAYMENT("결제", false),

    /** 환불 — 취소 · 변경으로 기존 티켓의 돈을 돌려줌, 묶음권의 그날 몫. */
    REFUND("환불", true),

    /** 배상 — 입장료와 별도로 주는 10%. 실 결제액에서 빼지 않는다. */
    COMPENSATION("배상", false),

    /** 지연 환급 — 30분 이상 늦게 시작한 회차에 입장한 티켓에 입장료의 10%를 돌려줌(docs/08 "공연 지연"). */
    DELAY_REFUND("지연 환급", true);

    private final String label;
    private final boolean returnsAdmissionFee;

    PaymentHistoryEntryKind(String label, boolean returnsAdmissionFee) {
        this.label = label;
        this.returnsAdmissionFee = returnsAdmissionFee;
    }

    /** 화면에 보이는 종류 — "결제", "환불", "배상", "지연 환급". */
    public String label() {
        return label;
    }

    /** 결제 줄인지 — 실 결제액에 더한다. */
    public boolean isPayment() {
        return this == PAYMENT;
    }

    /** 낸 입장료를 돌려준 줄인지 — 환불 · 지연 환급. 실 결제액에서 뺀다. 배상은 아니다. */
    public boolean returnsAdmissionFee() {
        return returnsAdmissionFee;
    }
}
