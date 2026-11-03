package com.ticketon.domain.booking;

import com.ticketon.domain.account.AudienceAction;

/**
 * 무엇을 하려고 자격을 묻는지 — 새 예매 / 예매 변경. 둘 다 이용 정지 중에는 막힌다(docs/01 "관객 이용 정지 — 새 예매 · 변경 · 신고").
 * <p>
 * 변경은 기존 티켓을 쥔 채 새 회차 · 좌석을 잡는 일이고, 계산뿐 아니라 처리도 "취소 + 재예매"다(docs/08 "예매 변경"). 그래서 새로 잡는 쪽은 새
 * 예매와 같은 판정(판매 중 · 그 회차 예매 종료 전 · 연령 · 회차 선택 제약 · 1인 총 매수)을 받는다. 다른 점은 둘이다.
 * <ul>
 *   <li>계정이 할 수 있는 일 — 새 예매는 {@link AudienceAction#BOOK}, 변경은 {@link AudienceAction#CHANGE_BOOKING}.</li>
 *   <li>판매 중단 문구 — 변경은 "현재 판매가 중단되어 변경할 수 없습니다. 취소는 가능합니다."(docs/04 "판매 중단").</li>
 * </ul>
 * 변경할 티켓 쪽의 판정(변경 기한 · 묶음권 · 초대권은 변경 불가)은 변경 흐름(#80 · #81)이 한다. 확인 필요 (#223) — 변경할 티켓은 곧 취소되므로
 * 부르는 쪽이 이미 가진 티켓에서 빼고 넘긴다고 보았다(취소 + 재예매와 같게). 그래서 판매 중에 매수 제한을 낮추거나 회차 선택 제약을 건 뒤에는 이미
 * 산 관객의 좌석 변경도 새 한도에 막힐 수 있다(4매를 산 뒤 날짜별 2매로 낮추면 같은 회차 안에서 1매를 바꿔도 남은 3매 + 1매 > 2매). docs/04는
 * "신규 예매부터 적용", "산 사람의 조건은 관객에게 유리한 쪽으로만"이라 하고 docs/08은 "변경 = 취소 + 재예매"라 해서 둘이 갈린다.
 */
public enum BookingPurpose {

    /** 새 예매 — 행사 페이지의 [예매하기]. */
    NEW_BOOKING(AudienceAction.BOOK, BookingErrorCode.SALES_PAUSED),

    /** 예매 변경 — 마이페이지 예매 카드의 좌석 · 회차 변경. */
    CHANGE(AudienceAction.CHANGE_BOOKING, BookingErrorCode.CHANGE_SALES_PAUSED);

    private final AudienceAction audienceAction;
    private final BookingErrorCode salesPausedCode;

    BookingPurpose(AudienceAction audienceAction, BookingErrorCode salesPausedCode) {
        this.audienceAction = audienceAction;
        this.salesPausedCode = salesPausedCode;
    }

    /** 이 일을 하려면 계정이 할 수 있어야 하는 일. */
    AudienceAction audienceAction() {
        return audienceAction;
    }

    /** 판매가 멈춘 행사에서 막을 때의 코드. */
    BookingErrorCode salesPausedCode() {
        return salesPausedCode;
    }
}
