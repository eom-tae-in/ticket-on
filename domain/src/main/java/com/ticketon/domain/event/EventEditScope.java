package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 상태별 수정 범위 — 행사 상태가 정하는, 무엇을 고칠 수 있는지의 큰 틀(docs/04 "상태별로 할 수 있는 것"). <b>상태마다 무엇을 고칠 수 있는지는
 * 여기 한곳에서 판정한다</b>({@link #availabilityOf}).
 * <table>
 *   <caption>상태별로 할 수 있는 것 (docs/04)</caption>
 *   <tr><th>상태</th><th>편집</th><th>범위</th></tr>
 *   <tr><td>작성 중</td><td>전부</td><td>{@link #EVERYTHING}</td></tr>
 *   <tr><td>판매 대기</td><td>전부 (예매가 아직 없음). 단 초대권을 먼저 발권했으면 초대권이 걸린 것에 잠금 항목이 적용됨</td>
 *       <td>{@link #EVERYTHING_EXCEPT_INVITED}</td></tr>
 *   <tr><td>판매 중 · 판매 중단</td><td>예매가 열린 뒤 수정 범위 적용</td><td>{@link #AFTER_BOOKING_OPENED}</td></tr>
 *   <tr><td>판매 종료 ~ 행사 종료</td><td>입구 목록 · 검표 계정만</td><td>{@link #ON_SITE_ONLY}</td></tr>
 *   <tr><td>행사 종료 이후 (정산 완료 · 취소됨 포함)</td><td>없음. 전부 고정</td><td>{@link #NOTHING}</td></tr>
 * </table>
 * 판매 중단은 "판매 중"의 변형이라 판매 중과 같다. 취소된 행사도 끝난 행사와 같다 — 복제는 되고 편집은 안 된다(docs/04 "행사가 취소된 경우도
 * 같습니다", docs/13 "지난 행사"). 판매가 끝나면 도면이 고정되는 게 중요하다 — 티켓은 좌석을 가리키기만 하므로 끝난 행사의 도면이 바뀌면 지난
 * 예매 내역의 좌석 번호가 바뀐다(docs/04 "도면이 고정되는 게 중요합니다").
 */
public enum EventEditScope {

    /** 작성 중 — 전부 고친다. */
    EVERYTHING,

    /** 판매 대기 — 전부 고친다. 초대권이 걸린 것은 예매가 열린 뒤와 같은 규칙이 오픈 전부터 붙는다(docs/09). */
    EVERYTHING_EXCEPT_INVITED,

    /** 판매 중 · 판매 중단 — 예매가 열린 뒤 수정 범위(항목마다 {@link EventEditTarget#afterBookingOpened}). */
    AFTER_BOOKING_OPENED,

    /** 판매 종료 — 입구 목록 · 검표 계정만({@link EventEditTarget#isEditableAfterSalesClosed}). */
    ON_SITE_ONLY,

    /** 행사 종료 · 정산 완료 · 취소됨 — 아무것도 고치지 않는다. */
    NOTHING;

    /** 그 상태의 수정 범위. */
    public static EventEditScope of(EventStatus status) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case DRAFT -> EVERYTHING;
            case AWAITING_OPEN -> EVERYTHING_EXCEPT_INVITED;
            case ON_SALE, SALES_PAUSED -> AFTER_BOOKING_OPENED;
            case SALES_CLOSED -> ON_SITE_ONLY;
            case ENDED, SETTLED, CANCELLED -> NOTHING;
        };
    }

    /** 이 범위에서 그 항목을 고칠 수 있는지. */
    public EventEditAvailability availabilityOf(EventEditTarget target) {
        Objects.requireNonNull(target, "target");
        return switch (this) {
            case EVERYTHING -> EventEditAvailability.ALLOWED;
            case EVERYTHING_EXCEPT_INVITED -> target.isLockedByInvitationsBeforeOpening()
                    ? EventEditAvailability.ALLOWED_WITH_RULES
                    : EventEditAvailability.ALLOWED;
            case AFTER_BOOKING_OPENED -> target.afterBookingOpened();
            case ON_SITE_ONLY -> target.isEditableAfterSalesClosed() ? EventEditAvailability.ALLOWED : EventEditAvailability.LOCKED;
            case NOTHING -> EventEditAvailability.LOCKED;
        };
    }
}
