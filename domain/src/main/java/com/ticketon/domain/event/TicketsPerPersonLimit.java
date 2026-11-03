package com.ticketon.domain.event;

import com.ticketon.domain.policy.QuantityPolicy;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * 1인 총 매수 — 한 사람이 정해진 범위에서 총 몇 장까지 살 수 있는지(docs/06 "매수 제한"). 제한이 없거나({@link Unlimited}), 매수와
 * 세는 범위를 정한다({@link Limited}).
 * <p>
 * 사람은 계정으로 센다 — 계정은 전화번호 하나에 하나라 사실상 사람 단위다(docs/06 "집계 기준"). 초대권은 포함하지 않는다. 실제로 세어 막는 일은
 * 예매 자격 판정이 한다.
 * <p>
 * 처음 값은 4매 · 날짜별이다({@link #defaultLimit}).
 */
public sealed interface TicketsPerPersonLimit {

    /** 제한 없음. */
    static TicketsPerPersonLimit unlimited() {
        return Unlimited.INSTANCE;
    }

    /**
     * 그 범위에서 한 사람이 maxTickets장까지.
     *
     * @param maxTickets 한도 (1 이상)
     * @param basis      세는 범위 — 날짜별 / 행사 전체
     */
    static TicketsPerPersonLimit of(int maxTickets, TicketsPerPersonBasis basis) {
        return new Limited(maxTickets, basis);
    }

    /**
     * 1인 총 매수의 처음 값 — 4매 · 날짜별(docs/06 "1인 총 매수", Figma O9, docs/99 D). 총 매수가 없으면 4석씩 여러 번 예매해 1회 선택 매수를 그대로
     * 우회한다. 날짜별이 처음 값인 까닭은 캐스팅을 바꿔 여러 날 보는 관객을 막지 않기 위해서다. 주최자가 제한 없음으로 바꿀 수는 있다.
     */
    static TicketsPerPersonLimit defaultLimit() {
        return of(QuantityPolicy.DEFAULT_MAX_TICKETS_PER_PERSON, TicketsPerPersonBasis.PER_DATE);
    }

    /** 한도가 있는지. */
    boolean isLimited();

    /**
     * 그 행사 전체에서 한 사람이 가질 수 있는 최대 매수 — 날짜별이면 한도 × 날짜 수, 행사 전체면 한도. 제한이 없으면 비어 있다.
     * 예: 4일 × 2회 공연에서 4매 제한 → 날짜별 16매, 행사 전체 4매(docs/06 "1인 총 매수").
     *
     * @param dateCount 행사의 날짜 수 (0 이상)
     */
    OptionalInt maximumTicketsInEvent(int dateCount);

    /** 제한 없음. */
    record Unlimited() implements TicketsPerPersonLimit {

        private static final Unlimited INSTANCE = new Unlimited();

        @Override
        public boolean isLimited() {
            return false;
        }

        @Override
        public OptionalInt maximumTicketsInEvent(int dateCount) {
            requireNonNegative(dateCount);
            return OptionalInt.empty();
        }
    }

    /**
     * 한도와 세는 범위.
     *
     * @param maxTickets 한도 (1 이상)
     * @param basis      세는 범위
     */
    record Limited(int maxTickets, TicketsPerPersonBasis basis) implements TicketsPerPersonLimit {

        public Limited {
            if (maxTickets < 1) {
                throw new IllegalArgumentException("1인 총 매수는 1장 이상이어야 합니다: " + maxTickets);
            }
            Objects.requireNonNull(basis, "basis");
        }

        @Override
        public boolean isLimited() {
            return true;
        }

        @Override
        public OptionalInt maximumTicketsInEvent(int dateCount) {
            requireNonNegative(dateCount);
            return switch (basis) {
                case PER_DATE -> OptionalInt.of(Math.multiplyExact(maxTickets, dateCount));
                case EVENT_TOTAL -> OptionalInt.of(maxTickets);
            };
        }
    }

    private static void requireNonNegative(int dateCount) {
        if (dateCount < 0) {
            throw new IllegalArgumentException("날짜 수는 0 이상이어야 합니다: " + dateCount);
        }
    }
}
