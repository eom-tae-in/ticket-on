package com.ticketon.domain.booking;

import com.ticketon.domain.error.ErrorCode;

/**
 * 예매 도메인의 오류 코드. 문구는 화면에 그대로 나간다.
 * <p>
 * 예매 자격 판정({@link BookingEligibilityJudge})이 막은 까닭은 {@link BookingRefusal}로 나간다 — 코드는 여기 상수 이름 그대로다.
 * <p>
 * 확인 필요 (#223) — 문서에 문구가 있는 것({@link #CHANGE_SALES_PAUSED}, docs/04 "판매 중단")밖은 모두 지었다. 경우는 docs/06 "전체 흐름" ·
 * docs/03 "행사 만들기 위저드"(미리보기)가 정했다.
 */
public enum BookingErrorCode implements ErrorCode {

    /** 운영진의 관객 화면 미리보기 링크로 예매하려 했다 — 그 링크로는 예매가 되지 않는다(docs/03 "미리보기 링크"). */
    PREVIEW_NOT_BOOKABLE("미리보기에서는 예매할 수 없습니다."),

    /** 아직 예매가 열리지 않았다 — 작성 중이거나 판매 대기(오픈 시각 전). 오픈 전 행사 페이지는 남은 시간을 보인다(docs/06 "오픈 전에도 행사 페이지는 공개"). */
    BOOKING_NOT_OPEN("아직 예매가 열리지 않았습니다."),

    /** 판매가 멈춘 행사에 새로 예매하려 했다 — 기존 예매는 유지되고 신규 판매만 멈춘다(docs/04 "판매 중단"). 목록 표시는 "판매 일시 중단"이다. */
    SALES_PAUSED("판매가 일시 중단되었습니다."),

    /** 판매가 멈춘 행사의 예매를 변경하려 했다 — 변경은 새 좌석을 선점하는 일이라 판매가 멈춘 상태와 모순된다. docs/04 "판매 중단"의 문구 그대로. */
    CHANGE_SALES_PAUSED("현재 판매가 중단되어 변경할 수 없습니다. 취소는 가능합니다."),

    /** 판매가 끝났다 — 마지막 회차의 예매 종료 시각이 지났거나 행사가 끝났다(docs/04 "전환 트리거"). */
    BOOKING_CLOSED("예매가 마감되었습니다."),

    /** 취소된 행사다 — 공연한 회차 없이 전부 취소했다(docs/04 "행사 취소"). */
    EVENT_CANCELLED("취소되어 예매할 수 없습니다.");

    private final String message;

    BookingErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
