package com.ticketon.domain.pricing;

import com.ticketon.domain.policy.QuantityPolicy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 금액(원) — 0원 이상의 원 단위 정수.
 * <p>
 * 가격 계산에서 끝자리를 다루는 규칙은 모두 여기 한곳에 둔다. 다른 객체는 이 메서드를 불러 쓰고 끝자리를 따로 자르지 않는다.
 * <ul>
 *   <li>퍼센트를 적용한 금액은 <b>100원 단위로 내린다</b>({@link #percentRoundedDown}) — 회차 퍼센트 조정, 묶음권 할인율, 정률 할인. 올리면 관객이
 *       손해다(docs/05 "회차별 가격 조정", docs/99 C "퍼센트 할인 반올림").</li>
 *   <li>비율로 나눈 몫은 <b>원 미만을 버린다</b>({@link #proportionalShare}) — 묶음권 그날 몫(docs/99 C "묶음권 그날 몫").</li>
 *   <li>한 금액을 여러 티켓에 나눌 때는 <b>비례로 나누고 합을 정확히 맞춘다</b>({@link #splitProportionally}) — 정액 쿠폰 분배(docs/05 "할인은 티켓
 *       단위로 기록합니다").</li>
 * </ul>
 * 계산이 {@code long}을 넘치면 {@link ArithmeticException}을 낸다(실제 가격으로는 생기지 않는다).
 *
 * @param won 금액(원), 0 이상
 */
public record Money(long won) implements Comparable<Money> {

    /** 0원. */
    public static final Money ZERO = new Money(0);

    private static final int WHOLE_PERCENT = 100;

    public Money {
        if (won < 0) {
            throw new IllegalArgumentException("금액은 0원 이상이어야 합니다: " + won);
        }
    }

    /**
     * 금액을 만든다.
     *
     * @throws IllegalArgumentException 0원보다 적으면
     */
    public static Money of(long won) {
        return new Money(won);
    }

    /** 금액들의 합. 비어 있으면 0원이다. */
    public static Money sum(Collection<Money> amounts) {
        long total = 0;
        for (Money amount : amounts) {
            total = Math.addExact(total, amount.won);
        }
        return new Money(total);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(won, other.won));
    }

    /**
     * 뺀 금액.
     *
     * @throws IllegalArgumentException 빼는 금액이 더 커서 0원 아래로 내려가면
     */
    public Money minus(Money other) {
        if (other.won > won) {
            throw new IllegalArgumentException("금액이 0원 아래로 내려갑니다: " + won + " − " + other.won);
        }
        return new Money(won - other.won);
    }

    /**
     * 이 금액의 {@code count}배.
     *
     * @throws IllegalArgumentException count가 음수면
     */
    public Money times(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("곱하는 수는 0 이상이어야 합니다: " + count);
        }
        return new Money(Math.multiplyExact(won, count));
    }

    /** 둘 중 작은 금액. */
    public Money min(Money other) {
        return isLessThan(other) ? this : other;
    }

    public boolean isZero() {
        return won == 0;
    }

    public boolean isLessThan(Money other) {
        return won < other.won;
    }

    public boolean isGreaterThan(Money other) {
        return won > other.won;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(won, other.won);
    }

    /**
     * 이 금액의 {@code percent}% — <b>100원 단위로 내린다</b>(docs/99 C "퍼센트 할인 반올림").
     * <ul>
     *   <li>140,000원의 93% = 130,200원 (3일권 7% 할인, docs/05)</li>
     *   <li>94,500원의 90% = 85,050원 → 85,000원 (얼리버드 10%, docs/05)</li>
     *   <li>50,000원의 80% = 40,000원 (프리뷰 −20%, docs/05)</li>
     * </ul>
     * 100%는 금액이 바뀌지 않으므로 끝자리도 내리지 않고 그대로 돌려준다 — 0% 할인이 100원 단위가 아닌 가격(12,345원)을 깎지 않게 한다. 확인 필요
     * (#48) — 문서는 "퍼센트 적용 시 100원 단위로 내림"만 적고, 바뀌지 않는 퍼센트는 말하지 않는다.
     *
     * @param percent 0 이상. 100보다 크면 더하는 조정이다(+10%는 110)
     * @throws IllegalArgumentException percent가 음수면
     */
    public Money percentRoundedDown(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("퍼센트는 0 이상이어야 합니다: " + percent);
        }
        if (percent == WHOLE_PERCENT) {
            return this;
        }
        long unit = QuantityPolicy.PERCENT_DISCOUNT_ROUNDING_UNIT_WON;
        long percentTimesWon = Math.multiplyExact(won, (long) percent);
        return new Money(percentTimesWon / (WHOLE_PERCENT * unit) * unit);
    }

    /**
     * 이 금액 × {@code part} ÷ {@code whole} — <b>원 미만은 버린다</b>(docs/99 C "묶음권 그날 몫"). 버리므로 나눈 몫을 모두 더해도 이 금액을 넘지
     * 않는다.
     * <ul>
     *   <li>130,200원 × 50,000 ÷ 140,000 = 46,500원 (3일권 토요일 몫, docs/08)</li>
     *   <li>89,500원 × 35,000 ÷ 105,000 = 29,833.3…원 → 29,833원 (쿠폰을 쓴 3일권 둘째 날 몫, docs/08)</li>
     * </ul>
     *
     * @param part  몫의 비중. whole보다 클 수 없다
     * @param whole 전체 비중. 0원보다 커야 한다
     * @throws IllegalArgumentException whole이 0원이거나 part가 whole보다 크면
     */
    public Money proportionalShare(Money part, Money whole) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(whole, "whole");
        if (whole.isZero()) {
            throw new IllegalArgumentException("전체 비중이 0원입니다");
        }
        if (part.isGreaterThan(whole)) {
            throw new IllegalArgumentException("몫의 비중이 전체보다 큽니다: " + part.won + " > " + whole.won);
        }
        return new Money(Math.multiplyExact(won, part.won) / whole.won);
    }

    /**
     * 이 금액을 {@code weights}의 비율로 나눈다 — <b>나눈 몫을 모두 더하면 정확히 이 금액이다</b>. 정액 쿠폰을 예매 건의 티켓마다 나눌 때 쓴다(4매에
     * 5,000원이면 장당 1,250원, docs/05 "할인은 티켓 단위로 기록합니다").
     * <ol>
     *   <li>몫마다 이 금액 × 비중 ÷ 비중 합계를 계산해 원 미만을 버린다.</li>
     *   <li>버려서 남은 원(몫의 수보다 적다)을 <b>버린 끝자리가 큰 몫부터</b> 1원씩 더한다. 끝자리가 같으면 <b>앞 순서부터</b> 더한다.</li>
     * </ol>
     * 같은 입력에는 늘 같은 결과다. 5,000원을 50,000원 티켓 세 장에 나누면 1,667 · 1,667 · 1,666원, 1,000원을 30,000 · 20,000 · 10,000원에
     * 나누면 500 · 333 · 167원(끝자리 .67이 .33보다 크다)이다. 확인 필요 (#48) — 나누어떨어지지 않을 때 남은 원을 어느 티켓에 줄지는 문서에 없다.
     * <p>
     * 이 금액이 비중 합계보다 크지 않으면 어떤 몫도 제 비중보다 크지 않다. 할인을 나눌 때는 부르는 쪽이 가격 합계까지만 넘긴다.
     *
     * @param weights 나눌 비중(티켓 가격). 티켓 순번대로 하나 이상, 합계가 0원보다 커야 한다
     * @return weights와 같은 순서의 몫
     * @throws IllegalArgumentException weights가 비었거나 합계가 0원이면
     */
    public List<Money> splitProportionally(List<Money> weights) {
        List<Money> orderedWeights = List.copyOf(weights);
        if (orderedWeights.isEmpty()) {
            throw new IllegalArgumentException("나눌 비중이 없습니다");
        }
        long totalWeight = sum(orderedWeights).won;
        if (totalWeight == 0) {
            throw new IllegalArgumentException("비중 합계가 0원입니다");
        }
        int count = orderedWeights.size();
        long[] shares = new long[count];
        long[] droppedRemainders = new long[count];
        long distributed = 0;
        for (int index = 0; index < count; index++) {
            long scaled = Math.multiplyExact(won, orderedWeights.get(index).won);
            shares[index] = scaled / totalWeight;
            droppedRemainders[index] = scaled % totalWeight;
            distributed += shares[index];
        }
        long leftover = won - distributed;
        List<Integer> leftoverOrder = IntStream.range(0, count).boxed()
                .sorted(Comparator.<Integer>comparingLong(index -> droppedRemainders[index]).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        for (int rank = 0; rank < leftover; rank++) {
            shares[leftoverOrder.get(rank)]++;
        }
        return Arrays.stream(shares).mapToObj(Money::new).toList();
    }

    @Override
    public String toString() {
        return won + "원";
    }
}
