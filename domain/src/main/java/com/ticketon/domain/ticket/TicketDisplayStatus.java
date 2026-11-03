package com.ticketon.domain.ticket;

/**
 * 관객 화면에 보이는 티켓 상태 — <b>저장하지 않고 계산한다</b>(docs/07 "관객 화면의 표시는 계산됩니다", docs/14 "저장하는 것과 계산하는 것").
 * <table>
 *   <caption>보이는 것과 판단 근거 (docs/07)</caption>
 *   <tr><th>보이는 것</th><th>무엇으로 판단하나</th></tr>
 *   <tr><td>유효</td><td>취소 안 됨 + 회차가 아직 안 지남</td></tr>
 *   <tr><td>입장 완료</td><td>입장 기록이 있음</td></tr>
 *   <tr><td>공연 종료</td><td>회차 날짜가 지남</td></tr>
 *   <tr><td>취소됨 · 사유</td><td>취소 시각이 있음</td></tr>
 * </table>
 * <b>"입장 완료"와 "만료"는 상태로 저장하지 않는다</b>(docs/99 B "티켓 '입장 완료' 상태", "티켓 '만료' 상태"). 입장은 여러 번 일어나 칸 하나에 담기지
 * 않고(3일권 · 재입장), 만료를 저장하면 매일 자정에 티켓 전체를 뒤져 바꿔야 하며 연기 때 수백 장을 되돌려야 한다. 계산은
 * {@link Ticket#display}가 한다.
 * <p>
 * 이 이름은 화면이 분기에 쓰므로 바꾸지 않는다.
 */
public enum TicketDisplayStatus {

    /** 유효 — 취소되지 않았고 가리키는 회차가 아직 다 끝나지 않았다. QR · 공유 버튼이 있다. */
    VALID("유효"),

    /** 입장 완료 — 유효하고 입장 기록이 있다. QR · 공유 버튼은 그대로 있다 — 넘기기 모드에서 이미 찍힌 티켓에 체크 표시로 보인다(docs/07). */
    ENTERED("입장 완료"),

    /** 공연 종료 — 가리키는 회차가 모두 끝났다. 버튼 없이 좌석 · 금액만 보인다(docs/07 "화면에서는 버튼만 빠집니다"). */
    PERFORMANCE_ENDED("공연 종료"),

    /** 취소됨 — 취소 시각이 있다. 사유와 함께 회색으로 남고 버튼이 없다(docs/07 "취소된 티켓도 예매 카드에 남습니다"). */
    CANCELLED("취소됨");

    private final String label;

    TicketDisplayStatus(String label) {
        this.label = label;
    }

    /** 화면에 보이는 이름 — "유효", "입장 완료", "공연 종료", "취소됨". */
    public String label() {
        return label;
    }
}
