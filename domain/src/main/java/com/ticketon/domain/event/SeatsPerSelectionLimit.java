package com.ticketon.domain.event;

import com.ticketon.domain.policy.QuantityPolicy;

/**
 * 1회 선택 매수 — 한 번 예매할 때 담을 수 있는 좌석 수이자, 동시에 선점할 수 있는 좌석 수(docs/06 "1회 선택 매수", "좌석 선점").
 * <p>
 * <b>자동 규칙이 아니라 주최자가 정한다.</b> 제한이 필요한지는 좌석 수가 아니라 수요 대비 공급에서 나오는데, 그 공연이 얼마나 팔릴지는 주최자가
 * 안다. 그래서 숫자를 받고 전체 좌석의 몇 %인지 되돌려 보여 주며, 위험선을 넘으면 경고만 한다 — 단체 관람을 받는 행사도 있어 막지 않는다
 * ({@link #feedbackFor}). 처음에는 {@value QuantityPolicy#DEFAULT_MAX_SEATS_PER_SELECTION}석을 채워 둔다({@link #defaultLimit}).
 * <p>
 * 4석으로 정했으면 5번째 좌석은 아예 눌리지 않는다. 묶음권은 티켓 장수로 센다 — 3일권 4장은 4매(docs/05 "매수 제한 계산").
 *
 * @param maxSeats 한 번에 담을 수 있는 좌석 수 (1 이상)
 */
public record SeatsPerSelectionLimit(int maxSeats) {

    private static final SeatsPerSelectionLimit DEFAULT = new SeatsPerSelectionLimit(QuantityPolicy.DEFAULT_MAX_SEATS_PER_SELECTION);

    public SeatsPerSelectionLimit {
        if (maxSeats < 1) {
            throw new IllegalArgumentException("1회 선택 매수는 1석 이상이어야 합니다: " + maxSeats);
        }
    }

    public static SeatsPerSelectionLimit of(int maxSeats) {
        return new SeatsPerSelectionLimit(maxSeats);
    }

    /** 처음 채워 두는 값 — {@value QuantityPolicy#DEFAULT_MAX_SEATS_PER_SELECTION}석(docs/06 "기본값 4석을 미리 채워둡니다"). */
    public static SeatsPerSelectionLimit defaultLimit() {
        return DEFAULT;
    }

    /**
     * 전체 좌석 수에 견준 피드백 — 비율과 경고(docs/06 "1회 선택 매수 — 주최자 입력 + 비율 피드백").
     * 예: 4석 / 전체 200석 → 2%, 경고 없음. 8석 / 전체 50석 → 16%, 경고.
     *
     * @param totalSeats 전체 좌석 수 (0 이상). 아직 구역을 만들지 않았으면 0
     */
    public SeatsPerSelectionFeedback feedbackFor(int totalSeats) {
        return new SeatsPerSelectionFeedback(maxSeats, totalSeats);
    }
}
