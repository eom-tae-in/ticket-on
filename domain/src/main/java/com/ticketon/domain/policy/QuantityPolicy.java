package com.ticketon.domain.policy;

import java.time.Period;

/**
 * docs/99 C "수량·비율" 표의 확정 숫자.
 * <p>
 * 조기예매 임박의 "종료 3일 전"처럼 99 C에서 이 표에 함께 적힌 기간도 여기 둔다.
 */
public final class QuantityPolicy {

    /** 1회 선택 매수 기본값 — 동시에 선점할 수 있는 좌석 수. 주최자가 조정한다. 묶음권은 티켓 장수로 센다. docs/06 "1회 선택 매수" */
    public static final int DEFAULT_MAX_SEATS_PER_SELECTION = 4;

    /** 1회 선택 매수 경고선 — 8석 초과 또는 전체의 10% 초과면 경고만 한다(막지 않음). docs/06 "1회 선택 매수" */
    public static final QuantityThreshold SEATS_PER_SELECTION_WARNING = new QuantityThreshold(8, Percent.of(10));

    /** 조기예매 "얼마 남지 않음" — 남은 몫이 30% 이하. docs/05 "임박 표시" */
    public static final Percent EARLY_BIRD_RUNNING_LOW = Percent.of(30);

    /** 조기예매 "마감 임박"(수량) — 남은 몫이 10% 이하 또는 10장 이하. docs/05 "임박 표시" */
    public static final QuantityThreshold EARLY_BIRD_ALMOST_GONE = new QuantityThreshold(10, Percent.of(10));

    /** 조기예매 "마감 임박"(기간) — 종료일 3일 이내. 수량과 기간 중 더 급한 쪽을 따른다. docs/05 "임박 표시" */
    public static final Period EARLY_BIRD_ENDING_SOON = Period.ofDays(3);

    /** 잔여 배지 "매진 임박" — 잔여 10% 이하 또는 10석 이하. 목록 카드에만 배지. docs/13 "잔여 좌석 표시" */
    public static final QuantityThreshold REMAINING_SEATS_BADGE = new QuantityThreshold(10, Percent.of(10));

    /** 묶음권 할인율 기본값 — 2일권 3%. 주최자가 조정한다. docs/05 "가격은 할인율로 계산합니다" */
    public static final Percent DEFAULT_BUNDLE_DISCOUNT_TWO_DAYS = Percent.of(3);

    /** 묶음권 할인율 기본값 — 3일권 7%. 주최자가 조정한다. docs/05 "가격은 할인율로 계산합니다" */
    public static final Percent DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS = Percent.of(7);

    /** 묶음권 할인율 기본값 — 4일권 10%. 주최자가 조정한다. docs/05 "가격은 할인율로 계산합니다" */
    public static final Percent DEFAULT_BUNDLE_DISCOUNT_FOUR_DAYS = Percent.of(10);

    /** 퍼센트 할인 반올림 단위 — 100원 단위로 내린다(올리면 관객이 손해). docs/05 "회차별 가격 조정", "가격은 할인율로 계산합니다" */
    public static final long PERCENT_DISCOUNT_ROUNDING_UNIT_WON = 100;

    /** 포스터 비율 — 3:4 세로 고정. docs/03 "기본 정보" */
    public static final AspectRatio POSTER_ASPECT_RATIO = new AspectRatio(3, 4);

    /** 연령대 통계 표시 기준 — 예매자 30명 이상일 때만, 행사 전체와 회차별을 각각. docs/11 "통계 — 연령대" */
    public static final int AGE_GROUP_STATS_MINIMUM_BOOKERS = 30;

    /** 배상률 — 입장료의 10%. 주최자 사정 취소·중단·출연자 변경일 때. 불가항력은 없음. docs/08 "법적 기준", "주최자 귀책" */
    public static final Percent COMPENSATION_RATE = Percent.of(10);

    /**
     * 관객 가입 최소 나이 — 만 14세. 가입일(한국 날짜)의 만 나이가 이보다 적으면 가입을 막고, 동의 항목에도 "만 14세 이상입니다"를 둔다.
     * 99 C 표에는 없지만 법이 정한 값이라 확정이다(개인정보보호법 제22조의2). docs/01 "전화번호"(만 14세 미만 차단), docs/99 A-3
     */
    public static final int MINIMUM_SIGNUP_AGE = 14;

    private QuantityPolicy() {
    }
}
