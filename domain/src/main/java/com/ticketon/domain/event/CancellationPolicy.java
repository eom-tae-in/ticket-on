package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.CancellationFeeTier;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.TimePolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 행사의 취소 규정 — 관람일까지 남은 날에 따른 공제율 표(docs/08 "규정은 기준 고정, 관대한 방향만 조정 가능").
 * <p>
 * <b>소비자분쟁해결기준을 처음 값으로 고정하고, 주최자는 관객에게 더 유리한 쪽으로만 조정한다</b>({@link #standard},
 * {@link TimePolicy#STANDARD_CANCELLATION_FEE_TIERS}). 주최자에게 자유롭게 정하게 하면 소규모 주최자가 "환불 불가"로 설정해 분쟁이 생기고, 관객은
 * 개별 주최자가 아니라 우리 서비스를 믿고 예매하므로 그 부담은 플랫폼이 진다.
 * <table>
 *   <caption>기준표</caption>
 *   <tr><th>취소 시점</th><th>공제율</th></tr>
 *   <tr><td>관람일 10일 전까지</td><td>없음 (전액 환급)</td></tr>
 *   <tr><td>9~7일 전</td><td>10%</td></tr>
 *   <tr><td>6~3일 전</td><td>20%</td></tr>
 *   <tr><td>2~1일 전</td><td>30%</td></tr>
 *   <tr><td>당일 (시작 전)</td><td>90%</td></tr>
 * </table>
 * <b>조정안은 모든 날에서 공제율이 기준 이하일 때만 받는다</b>({@link #of}). "공연 3일 전까지 전액 환불"은 받고, "7일 전부터 환불 불가"는 막는다.
 * 한 구간을 낮춰도 다른 구간을 기준보다 높이면 막는다.
 * <p>
 * 표는 구간({@link CancellationFeeTier} — "관람일 N일 전부터 P%")을 모은 것이다. 관람일 당일(0일 전)부터 시작하는 구간이 있어야 하고, 같은 날에서
 * 시작하는 구간이 둘일 수 없다. 받을 때 먼 날부터 차례로 늘어놓고, 더 먼 쪽 이웃과 공제율이 같아 덧붙는 것이 없는 구간은 합친다 — 그래서 같은 규정은
 * 어떻게 적어 넣었든 같은 값이다({@link #tiers}).
 * <p>
 * <b>예매 직후 예외는 늘 붙는다</b> — 예매 후 {@link TimePolicy#FULL_REFUND_PERIOD_AFTER_BOOKING 24시간} 이내 취소는 관람일
 * {@value TimePolicy#FULL_REFUND_AFTER_BOOKING_MINIMUM_DAYS_BEFORE}일 전까지 전액 환급한다. 기준의 일부라 조정안에서 뺄 수 없고, 조정 대상도
 * 아니다(조정은 구간의 공제율만 — docs/08의 조정 예시가 모두 구간이다). 이 예외와 공제 면제 · 취소 마감 · 금액 계산은 환불 계산이 한다.
 * <p>
 * 예매가 열린 뒤(판매 중)에는 규정을 바꿀 수 없다 — 관객마다 예매 시점의 규정이 따라다닌다. 그 잠금은 상태별 수정 범위가 정한다
 * (docs/04 "예매가 열린 뒤 수정 범위").
 *
 * @param tiers 공제율 구간 — 먼 날부터, 겹치는 구간을 합친 것
 */
public record CancellationPolicy(List<CancellationFeeTier> tiers) {

    private static final Comparator<CancellationFeeTier> NEAREST_FIRST = Comparator.comparingInt(CancellationFeeTier::fromDaysBefore);

    private static final List<CancellationFeeTier> STANDARD_TIERS = normalize(TimePolicy.STANDARD_CANCELLATION_FEE_TIERS);

    private static final CancellationPolicy STANDARD = new CancellationPolicy(STANDARD_TIERS);

    /**
     * @throws IllegalArgumentException 구간이 없거나, 관람일 당일부터 시작하는 구간이 없거나, 같은 날에서 시작하는 구간이 둘이면
     * @throws RuleViolationException   어느 날이라도 공제율이 기준보다 높으면({@link EventErrorCode#CANCELLATION_POLICY_STRICTER_THAN_STANDARD})
     */
    public CancellationPolicy {
        tiers = normalize(tiers);
        requireNoStricterThanStandard(tiers);
    }

    /** 기준표 그대로의 규정 — 처음 값. */
    public static CancellationPolicy standard() {
        return STANDARD;
    }

    /**
     * 주최자가 조정한 규정. 순서는 상관없다.
     * 예: "공연 3일 전까지 전액" = {@code [(3일 전, 0%), (1일 전, 30%), (당일, 90%)]}.
     *
     * @throws IllegalArgumentException 구간이 없거나, 관람일 당일부터 시작하는 구간이 없거나, 같은 날에서 시작하는 구간이 둘이면
     * @throws RuleViolationException   어느 날이라도 공제율이 기준보다 높으면({@link EventErrorCode#CANCELLATION_POLICY_STRICTER_THAN_STANDARD})
     */
    public static CancellationPolicy of(List<CancellationFeeTier> tiers) {
        return new CancellationPolicy(tiers);
    }

    /** 기준표 그대로인지 — 주최자가 조정하지 않았거나, 조정했다가 기준과 같게 되돌렸으면 true. */
    public boolean isStandard() {
        return equals(STANDARD);
    }

    /**
     * 관람일 그만큼 전에 취소할 때 이 표가 정한 공제율. 표만 읽는다 — 예매 직후 예외 · 공제 면제 · 취소 마감은 보지 않는다.
     * 예: 기준표에서 5일 전 → 20%, 당일 → 90%.
     *
     * @param daysBefore 관람일까지 남은 달력 일수 (0 이상, 당일이 0)
     */
    public Percent deductionRateOn(long daysBefore) {
        if (daysBefore < 0) {
            throw new IllegalArgumentException("관람일까지 남은 일수는 0 이상이어야 합니다: " + daysBefore);
        }
        return rateOn(tiers, daysBefore);
    }

    private static List<CancellationFeeTier> normalize(List<CancellationFeeTier> tiers) {
        Objects.requireNonNull(tiers, "tiers");
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("취소 규정에 구간이 하나도 없습니다");
        }
        List<CancellationFeeTier> nearestFirst = new ArrayList<>(tiers);
        nearestFirst.forEach(tier -> Objects.requireNonNull(tier, "tier"));
        nearestFirst.sort(NEAREST_FIRST);
        if (nearestFirst.getFirst().fromDaysBefore() != 0) {
            throw new IllegalArgumentException("취소 규정에 관람일 당일부터 시작하는 구간이 없습니다: " + tiers);
        }
        requireDistinctStartDays(nearestFirst);
        List<CancellationFeeTier> merged = new ArrayList<>();
        for (CancellationFeeTier tier : nearestFirst) {
            if (merged.isEmpty() || !merged.getLast().deductionRate().equals(tier.deductionRate())) {
                merged.add(tier);
            }
        }
        return List.copyOf(merged.reversed());
    }

    /** 합치기 전에 입력 전체를 본다 — 합쳐져 사라질 구간이 끼어 있어도 넣은 순서와 상관없이 같은 날 중복을 막는다. */
    private static void requireDistinctStartDays(List<CancellationFeeTier> nearestFirst) {
        for (int index = 1; index < nearestFirst.size(); index++) {
            int startDay = nearestFirst.get(index).fromDaysBefore();
            if (nearestFirst.get(index - 1).fromDaysBefore() == startDay) {
                throw new IllegalArgumentException("같은 날에서 시작하는 구간이 둘입니다: 관람일 " + startDay + "일 전");
            }
        }
    }

    private static void requireNoStricterThanStandard(List<CancellationFeeTier> tiers) {
        SortedSet<Integer> boundaries = new TreeSet<>();
        tiers.forEach(tier -> boundaries.add(tier.fromDaysBefore()));
        STANDARD_TIERS.forEach(tier -> boundaries.add(tier.fromDaysBefore()));
        for (int daysBefore : boundaries) {
            Percent adjusted = rateOn(tiers, daysBefore);
            Percent standard = rateOn(STANDARD_TIERS, daysBefore);
            if (adjusted.value() > standard.value()) {
                throw new RuleViolationException(EventErrorCode.CANCELLATION_POLICY_STRICTER_THAN_STANDARD,
                        "관람일 " + daysBefore + "일 전 — 조정 " + adjusted + ", 기준 " + standard);
            }
        }
    }

    private static Percent rateOn(List<CancellationFeeTier> farthestFirst, long daysBefore) {
        for (CancellationFeeTier tier : farthestFirst) {
            if (tier.fromDaysBefore() <= daysBefore) {
                return tier.deductionRate();
            }
        }
        throw new IllegalStateException("관람일 당일부터 시작하는 구간이 없습니다: " + farthestFirst);
    }
}
