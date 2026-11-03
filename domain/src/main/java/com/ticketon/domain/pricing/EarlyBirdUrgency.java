package com.ticketon.domain.pricing;

import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.QuantityPolicy;
import com.ticketon.domain.policy.QuantityThreshold;
import com.ticketon.domain.policy.TimePolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * 조기예매 임박 단계 — 관객에게 남은 몫을 숫자 대신 보여 주는 표시(docs/05 "임박 표시", docs/99 C "조기예매 임박 표시"). 뒤로 갈수록 급하다.
 * <table>
 *   <caption>남은 상황과 관객이 보는 것 (docs/05)</caption>
 *   <tr><th>남은 상황</th><th>관객이 보는 것</th><th>단계</th></tr>
 *   <tr><td>30% 넘게 남음</td><td>표시 없음</td><td>{@link #NONE}</td></tr>
 *   <tr><td>30% 이하({@link QuantityPolicy#EARLY_BIRD_RUNNING_LOW})</td><td>얼마 남지 않음</td><td>{@link #RUNNING_LOW}</td></tr>
 *   <tr><td>10% 이하, 또는 10장 이하이면서 절반 이상 나감({@link QuantityPolicy#EARLY_BIRD_ALMOST_GONE},
 *       {@link QuantityPolicy#EARLY_BIRD_ALMOST_GONE_CLAIMED_SHARE})</td><td>마감 임박</td><td>{@link #ALMOST_GONE}</td></tr>
 *   <tr><td>끝나기 3일(72시간) 전부터({@link TimePolicy#EARLY_BIRD_ENDING_SOON})</td><td>마감 임박</td><td>{@link #ALMOST_GONE}</td></tr>
 * </table>
 * <b>수량과 기간 중 더 급한 쪽</b>을 따르고, <b>한 번 올라간 단계는 내려가지 않는다</b>({@link EarlyBird#urgency}). "3장 남음" 같은 정확한 숫자를
 * 띄우지 않는 까닭은, 오래 안 팔리면 흥행 실패로 보이기 때문이다.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로(도달한 최고 단계) 바꾸지 않는다.
 */
public enum EarlyBirdUrgency {

    /** 표시 없음 — 30% 넘게 남았고 끝나기까지 72시간 넘게 남았다. */
    NONE,

    /** 얼마 남지 않음 — 남은 몫이 수량의 30% 이하. */
    RUNNING_LOW,

    /** 마감 임박 — 남은 몫이 수량의 10% 이하, 또는 10장 이하이면서 절반 이상 나감. 또는 끝나기 72시간 전부터. */
    ALMOST_GONE;

    private static final long PERCENT = 100;

    /**
     * 남은 몫으로 본 단계. 경계는 모두 "이하"이고 비율은 반올림하지 않는다 — 100장 중 30장 남으면 얼마 남지 않음, 31장이면 표시 없음.
     * <p>
     * <b>"10장 이하"에는 절반 이상 나갔을 때만이라는 조건이 붙는다</b>(docs/05 "임박 표시") — 10장짜리 조기예매가 한 장도 안 나갔는데 마감 임박이면
     * 거짓 재촉이다(docs/15 한서윤). 잔여 배지와 같은 규칙이다(docs/13). 나간 몫은 사용 수다 — 선점하는 순간 잡혀 돌아오지 않는 몫이라 남은 몫은
     * 늘 수량 − 사용 수이고, "절반 이상 나감"은 <b>남은 몫 × 2 ≤ 수량</b>과 같다. 10장짜리는 5장 남을 때, 7장짜리는 3장 남을 때(4장 남으면 3/7 =
     * 42.9%만 나감) 마감 임박이 된다. 수량이 20장 이상이면 10장 남았을 때 이미 절반 넘게 나갔으므로 이 조건은 20장 미만에서만 걸린다.
     * <p>
     * 수량이 36장 이하면 마감 임박이 30%보다 먼저 와 얼마 남지 않음을 건너뛴다 — 30장이면 11장 남을 때까지 표시 없음, 10장 남으면 마감 임박. 문서
     * 표를 그대로 따른 결과다.
     *
     * @param remaining 남은 몫, 0 이상 limit 이하
     * @param limit     수량, 1 이상
     */
    static EarlyBirdUrgency byQuantity(int remaining, int limit) {
        if (limit < 1 || remaining < 0 || remaining > limit) {
            throw new IllegalArgumentException("남은 몫이 수량 범위 밖입니다: " + remaining + " / " + limit);
        }
        if (isAlmostGone(remaining, limit)) {
            return ALMOST_GONE;
        }
        if (isAtMostPercentOf(remaining, limit, QuantityPolicy.EARLY_BIRD_RUNNING_LOW)) {
            return RUNNING_LOW;
        }
        return NONE;
    }

    /**
     * 기간으로 본 단계 — 끝 순간에서 72시간 전부터 마감 임박이다({@link TimePolicy#EARLY_BIRD_ENDING_SOON}). 날짜로 세지 않는다 — 3/20 18:00에 끝나면
     * 3/17 0시가 아니라 3/17 18:00부터다(docs/99 C "조기예매 '끝나기 3일 전'").
     */
    static EarlyBirdUrgency byPeriod(DiscountPeriod period, Instant now) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        Instant endingSoonFrom = period.endsAt().minus(TimePolicy.EARLY_BIRD_ENDING_SOON);
        return now.isBefore(endingSoonFrom) ? NONE : ALMOST_GONE;
    }

    /** 둘 중 더 급한 단계. */
    EarlyBirdUrgency moreUrgent(EarlyBirdUrgency other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) >= 0 ? this : other;
    }

    private static boolean isAlmostGone(int remaining, int limit) {
        QuantityThreshold almostGone = QuantityPolicy.EARLY_BIRD_ALMOST_GONE;
        boolean withinPercent = isAtMostPercentOf(remaining, limit, almostGone.percentOfTotal());
        boolean fewAndMostlyClaimed = remaining <= almostGone.count()
                && isClaimedAtLeast(limit - remaining, limit, QuantityPolicy.EARLY_BIRD_ALMOST_GONE_CLAIMED_SHARE);
        return withinPercent || fewAndMostlyClaimed;
    }

    private static boolean isAtMostPercentOf(int remaining, int limit, Percent percent) {
        return remaining * PERCENT <= (long) limit * percent.value();
    }

    private static boolean isClaimedAtLeast(int claimed, int limit, Percent share) {
        return claimed * PERCENT >= (long) limit * share.value();
    }
}
