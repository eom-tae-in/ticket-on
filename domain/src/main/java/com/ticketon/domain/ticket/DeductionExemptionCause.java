package com.ticketon.domain.ticket;

/**
 * 공제 면제가 붙은 까닭 — 넷 모두 주최자 쪽 사정으로 산 표의 내용이 바뀐 경우다(docs/04 "공제 면제", docs/08 "공제 면제가 붙은 티켓"). 면제가
 * 붙은 티켓은 이후 언제 취소해도 공제 0%다. 그 행사, 그 티켓에만 붙는 권리라 티켓이 직접 갖는다(docs/07).
 * <table>
 *   <caption>공제 면제 (docs/04)</caption>
 *   <tr><th>경우</th><th>붙는 시점</th><th>취소 시 배상</th></tr>
 *   <tr><td>연기</td><td>관리자 승인</td><td>없음</td></tr>
 *   <tr><td>장소 변경</td><td>관리자 승인</td><td>없음</td></tr>
 *   <tr><td>회차 시간 수정</td><td>주최자 수정</td><td>없음</td></tr>
 *   <tr><td>출연자 변경</td><td>주최자 알림</td><td>10% 배상</td></tr>
 * </table>
 * 여러 까닭이 겹쳐 붙을 수 있다(연기 뒤 출연자 변경 등). 공제율 · 배상액 계산은 환불 계산(#78)이 한다.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum DeductionExemptionCause {

    /** 연기 — 회차 날짜가 바뀌었다(관리자 승인). */
    POSTPONEMENT("연기"),

    /** 장소 변경 — 장소가 바뀌었다(관리자 승인). 모든 회차의 티켓에 붙는다. */
    VENUE_CHANGE("장소 변경"),

    /** 회차 시간 수정 — 티켓이 있는 회차의 시간을 주최자가 고쳤다(회차 시작 1주일 전까지). */
    SESSION_TIME_CHANGE("회차 시간 수정"),

    /**
     * 출연자 변경 — 주요 출연자가 바뀌었다(주최자 알림). 면제와 함께 <b>취소 시 10% 배상</b>이 붙는다. <b>묶음권에는 붙지 않는다</b> — 묶음권이 받는
     * 권리는 바뀐 날만 포기하는 것 하나다(docs/04 "묶음권은 바뀐 날만 포기합니다").
     */
    PERFORMER_CHANGE("출연자 변경");

    private final String label;

    DeductionExemptionCause(String label) {
        this.label = label;
    }

    /** 화면에 보이는 까닭 — "연기", "출연자 변경". */
    public String label() {
        return label;
    }
}
