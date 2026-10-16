package com.ticketon.domain.event;

import com.ticketon.domain.policy.QuantityPolicy;
import com.ticketon.domain.policy.QuantityThreshold;
import java.util.OptionalInt;

/**
 * 1회 선택 매수를 전체 좌석 수에 견준 피드백(docs/06 "1회 선택 매수 — 주최자 입력 + 비율 피드백").
 * <blockquote>1회 선택 매수 [4] 석 — 전체 200석의 2%입니다.</blockquote>
 * <b>위험선(8석 초과 또는 전체의 10% 초과)을 넘으면 경고만 한다</b>({@link QuantityPolicy#SEATS_PER_SELECTION_WARNING}). 막지 않는다.
 * <blockquote>8석은 전체 50석의 16%입니다. 한 사람이 객석의 상당 부분을 가져갈 수 있습니다.</blockquote>
 * <p>
 * 경고는 비율을 반올림하기 전 값으로 본다 — 8석 / 80석(10%)은 "10% 초과"가 아니고, 9석 / 80석(11.25%)은 넘는다.
 * 보여 줄 비율은 올림한 정수 퍼센트다({@link #percentOfTotal}): 경고선을 넘은 비율이 "10%"로 보이면 경고와 숫자가 어긋나기 때문이다
 * (반올림 방식은 문서에 없어 정한 것 — 확인 필요 #35).
 *
 * @param maxSeats   1회 선택 매수 (1 이상)
 * @param totalSeats 전체 좌석 수 (0 이상). 아직 구역을 만들지 않았으면 0이고, 그때는 비율 없이 좌석 수 경고만 본다
 */
public record SeatsPerSelectionFeedback(int maxSeats, int totalSeats) {

    private static final long PERCENT = 100;

    public SeatsPerSelectionFeedback {
        if (maxSeats < 1) {
            throw new IllegalArgumentException("1회 선택 매수는 1석 이상이어야 합니다: " + maxSeats);
        }
        if (totalSeats < 0) {
            throw new IllegalArgumentException("전체 좌석 수는 0 이상이어야 합니다: " + totalSeats);
        }
    }

    /**
     * 전체 좌석에서 차지하는 비율 — 올림한 정수 퍼센트. 4석 / 200석 → 2, 3석 / 200석(1.5%) → 2, 8석 / 50석 → 16.
     * 1회 선택 매수가 전체보다 많으면 100을 넘는다(막지 않으므로). 전체 좌석 수가 0이면 비어 있다.
     */
    public OptionalInt percentOfTotal() {
        if (totalSeats == 0) {
            return OptionalInt.empty();
        }
        long numerator = maxSeats * PERCENT;
        return OptionalInt.of((int) ((numerator + totalSeats - 1) / totalSeats));
    }

    /** 좌석 수 위험선(8석)을 넘는지 — 9석부터. */
    public boolean exceedsSeatCount() {
        return maxSeats > warningLine().count();
    }

    /** 전체 대비 위험선(10%)을 넘는지 — 반올림 전 비율로 본다. 전체 좌석 수가 0이면 보지 않는다. */
    public boolean exceedsShareOfTotal() {
        if (totalSeats == 0) {
            return false;
        }
        return maxSeats * PERCENT > (long) totalSeats * warningLine().percentOfTotal().value();
    }

    /** 경고를 띄우는지 — 둘 중 하나만 넘어도 띄운다. 경고는 막지 않는다. */
    public boolean isWarning() {
        return exceedsSeatCount() || exceedsShareOfTotal();
    }

    private static QuantityThreshold warningLine() {
        return QuantityPolicy.SEATS_PER_SELECTION_WARNING;
    }
}
