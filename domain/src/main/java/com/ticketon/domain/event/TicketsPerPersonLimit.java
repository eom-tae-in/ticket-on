package com.ticketon.domain.event;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * 1인 총 매수 — 한 사람이 정해진 범위에서 총 몇 장까지 살 수 있는지(docs/06 "매수 제한"). 제한이 없거나({@link Unlimited}), 매수와
 * 세는 범위를 정한다({@link Limited}).
 * <p>
 * 사람은 계정으로 센다 — 계정은 전화번호 하나에 하나라 사실상 사람 단위다(docs/06 "집계 기준"). 초대권은 포함하지 않는다. 실제로 세어 막는 일은
 * 예매 자격 판정이 한다.
 * <p>
 * 처음 값은 제한 없음이다({@link #defaultLimit}, 확인 필요 #35).
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
     * 확인 필요 (#35, 문서에 없음) — 1인 총 매수의 처음 값. 제한 없음을 제안한다: 1인 총 매수는 문서에 기본값이 없고, 1회 선택 매수(기본 4석)와
     * 회차 선택 제약(기본 "제한 없음")과 달리 처음부터 걸어 둘 숫자가 정해져 있지 않다. 한 번에 잡는 양은 1회 선택 매수가 이미 막는다
     * (docs/06 "선점 반복 악용"). 숫자로 정해지면 세는 범위(날짜별 / 행사 전체)의 처음 값도 함께 정한다.
     */
    static TicketsPerPersonLimit defaultLimit() {
        return unlimited();
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
