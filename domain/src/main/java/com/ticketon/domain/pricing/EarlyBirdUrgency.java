package com.ticketon.domain.pricing;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.QuantityPolicy;
import com.ticketon.domain.policy.QuantityThreshold;
import java.time.Instant;
import java.util.Objects;

/**
 * 조기예매 임박 단계 — 관객에게 남은 몫을 숫자 대신 보여 주는 표시(docs/05 "임박 표시", docs/99 C "조기예매 임박 표시"). 뒤로 갈수록 급하다.
 * <table>
 *   <caption>남은 상황과 관객이 보는 것 (docs/05)</caption>
 *   <tr><th>남은 상황</th><th>관객이 보는 것</th><th>단계</th></tr>
 *   <tr><td>30% 넘게 남음</td><td>표시 없음</td><td>{@link #NONE}</td></tr>
 *   <tr><td>30% 이하({@link QuantityPolicy#EARLY_BIRD_RUNNING_LOW})</td><td>얼마 남지 않음</td><td>{@link #RUNNING_LOW}</td></tr>
 *   <tr><td>10% 이하 또는 10장 이하({@link QuantityPolicy#EARLY_BIRD_ALMOST_GONE})</td><td>마감 임박</td><td>{@link #ALMOST_GONE}</td></tr>
 *   <tr><td>종료일 3일 이내({@link QuantityPolicy#EARLY_BIRD_ENDING_SOON})</td><td>마감 임박</td><td>{@link #ALMOST_GONE}</td></tr>
 * </table>
 * <b>수량과 기간 중 더 급한 쪽</b>을 따르고, <b>한 번 올라간 단계는 내려가지 않는다</b>({@link EarlyBird#urgency}). "3장 남음" 같은 정확한 숫자를
 * 띄우지 않는 까닭은, 오래 안 팔리면 흥행 실패로 보이기 때문이다.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로(도달한 최고 단계) 바꾸지 않는다.
 */
public enum EarlyBirdUrgency {

    /** 표시 없음 — 30% 넘게 남았고 종료까지 3일 넘게 남았다. */
    NONE,

    /** 얼마 남지 않음 — 남은 몫이 수량의 30% 이하. */
    RUNNING_LOW,

    /** 마감 임박 — 남은 몫이 수량의 10% 이하 또는 10장 이하, 또는 종료 3일 이내. */
    ALMOST_GONE;

    /**
     * 남은 몫으로 본 단계. 경계는 모두 "이하"다 — 100장 중 30장 남으면 얼마 남지 않음, 31장이면 표시 없음.
     * <p>
     * 수량이 10장 이하인 조기예매는 처음부터 마감 임박이다(10장 이하). 수량이 36장 이하면 30%가 11장에 못 미쳐 얼마 남지 않음을 건너뛴다 — 30장이면
     * 11장 남을 때까지 표시 없음, 10장 남으면 마감 임박. 문서 표를 그대로 따른 결과다.
     *
     * @param remaining 남은 몫, 0 이상 limit 이하
     * @param limit     수량, 1 이상
     */
    static EarlyBirdUrgency byQuantity(int remaining, int limit) {
        if (limit < 1 || remaining < 0 || remaining > limit) {
            throw new IllegalArgumentException("남은 몫이 수량 범위 밖입니다: " + remaining + " / " + limit);
        }
        QuantityThreshold almostGone = QuantityPolicy.EARLY_BIRD_ALMOST_GONE;
        if (remaining <= almostGone.count() || isAtMostPercentOf(remaining, limit, almostGone.percentOfTotal().value())) {
            return ALMOST_GONE;
        }
        if (isAtMostPercentOf(remaining, limit, QuantityPolicy.EARLY_BIRD_RUNNING_LOW.value())) {
            return RUNNING_LOW;
        }
        return NONE;
    }

    /**
     * 기간으로 본 단계 — 끝 순간에서 3일(한국 달력)을 거슬러 올라간 순간부터 마감 임박이다. 3/10 0시에 끝나면 3/7 0시부터. 확인 필요 (#49) —
     * 문서의 "종료일 3일 이내"를 끝 순간 기준 3일(72시간)로 읽었다.
     */
    static EarlyBirdUrgency byPeriod(DiscountPeriod period, Instant now) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        Instant endingSoonFrom = KoreanTime.toInstant(KoreanTime.toDateTime(period.endsAt()).minus(QuantityPolicy.EARLY_BIRD_ENDING_SOON));
        return now.isBefore(endingSoonFrom) ? NONE : ALMOST_GONE;
    }

    /** 둘 중 더 급한 단계. */
    EarlyBirdUrgency moreUrgent(EarlyBirdUrgency other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) >= 0 ? this : other;
    }

    private static boolean isAtMostPercentOf(int remaining, int limit, int percent) {
        return (long) remaining * 100 <= (long) limit * percent;
    }
}
