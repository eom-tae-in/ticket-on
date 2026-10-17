package com.ticketon.domain.event;

/**
 * 내 행사 목록의 구역 — 주최 관리의 내 행사 목록은 행사를 상태에 따라 세 구역으로 나눠 보인다(docs/13 "3. 주최 관리 — 내 행사 목록", docs/11
 * "구조").
 * <table>
 *   <caption>구역과 상태</caption>
 *   <tr><th>구역</th><th>상태</th></tr>
 *   <tr><td>작성 중 — 목록 상단의 별도 구역(진행률 · 마지막 수정일 · [이어서 작성])</td><td>작성 중</td></tr>
 *   <tr><td>진행 중 행사</td><td>판매 대기 · 판매 중 · 판매 중단 · 판매 종료</td></tr>
 *   <tr><td>지난 행사 — 복제 가능, 편집 불가</td><td>행사 종료 · 정산 완료 · 취소됨</td></tr>
 * </table>
 * <b>판매 중단은 진행 중이다</b>(확인 필요 #45) — docs/13의 진행 중 행사 목록에는 이름이 없지만, 판매 중단은 "판매 중"의 변형이고
 * (docs/04 "시간이 운영 판단을 이깁니다") 기존 예매가 그대로 살아 있다. 지난 행사로 보내면 편집 불가 · 복제 대상으로 보이고, 작성 중도 아니다.
 * <p>
 * 구역은 저장된 행사 상태({@link Event#status})로 정한다. 시간이 정하는 전환(오픈 · 판매 종료 · 행사 종료)은 정해진 시각에 도는 작업이 상태에
 * 반영한다({@link Event#advanceByTime}).
 */
public enum EventListSection {

    /** 작성 중 — 위저드 진행 중, 임시저장 상태. 목록 상단에 따로 둔다(docs/04 "작성 중 행사 관리"). */
    DRAFT,

    /** 진행 중 행사 — 판매를 시작했고 행사가 아직 끝나지 않았다. */
    IN_PROGRESS,

    /** 지난 행사 — 행사가 끝났거나 정산까지 마쳤거나 취소됐다. */
    PAST;

    /** 그 상태의 행사가 놓이는 구역. */
    public static EventListSection of(EventStatus status) {
        return switch (status) {
            case DRAFT -> DRAFT;
            case AWAITING_OPEN, ON_SALE, SALES_PAUSED, SALES_CLOSED -> IN_PROGRESS;
            case ENDED, SETTLED, CANCELLED -> PAST;
        };
    }
}
