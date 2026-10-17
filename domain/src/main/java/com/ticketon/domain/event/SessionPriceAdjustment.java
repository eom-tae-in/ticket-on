package com.ticketon.domain.event;

/**
 * 회차별 가격 조정 — 회차마다 구역 기본가에 더하거나 빼는 값. 회차당 하나이고 그 회차의 모든 구역에 같이 적용된다(docs/05 "회차별 가격
 * 조정", docs/14 "회차 ├─ 가격 조정").
 * <blockquote>
 * S석 기본 50,000원<br>
 * 3/14(금) 조정 없음 → 50,000원<br>
 * 3/15(토) +10,000원 → 60,000원<br>
 * 3/12(수, 프리뷰) -20% → 40,000원
 * </blockquote>
 * <b>금액과 퍼센트 둘 다 받는다</b> — 평일 · 주말 차등, 프리뷰 할인, 캐스팅 차등. 회차마다 구역 가격을 전부 새로 받으면 8회차 × 4구역 = 32칸이
 * 되지만, 기본가는 하나 · 회차에는 조정값만 두면 입력이 회차 수만큼으로 줄어든다.
 * <ul>
 *   <li>{@link None} — 조정 없음. 처음 값이다.</li>
 *   <li>{@link Amount} — 금액(원) ±. 0이면 조정 없음과 같아 {@link None}으로 만든다.</li>
 *   <li>{@link Rate} — 퍼센트 ±. 0이면 {@link None}으로 만든다. -100% 이하는 받지 않는다 — 어느 구역이든 0원 이하가 된다. 돈을 받지 않는 표는
 *       초대권이다(docs/03 "초대권은 무료 발권").</li>
 * </ul>
 * 조정한 가격을 계산하는 일(퍼센트 적용 시 100원 단위 내림, 조정 뒤 가격이 0원 이하가 되는 조정 막기)은 가격 도메인(pricing)의 회차
 * 가격(SessionPrice, #48)이 한다. 저장할 때 막는 일은 #51에 넘긴다. 회차별 조정은
 * 기본 가격을 정하는 단계라 조기예매 · 쿠폰 같은 할인과 겹치는 것이 아니다(docs/05 "할인 중복 규칙").
 */
public sealed interface SessionPriceAdjustment {

    /** 조정 없음 — 처음 값. */
    static SessionPriceAdjustment none() {
        return None.INSTANCE;
    }

    /**
     * 금액 조정. 0이면 조정 없음이다.
     *
     * @param won 더할 금액(원). 빼려면 음수 — 예: +10,000원은 10000, -5,000원은 -5000
     */
    static SessionPriceAdjustment amount(long won) {
        return won == 0 ? none() : new Amount(won);
    }

    /**
     * 퍼센트 조정. 0이면 조정 없음이다.
     *
     * @param percent 더할 퍼센트. 빼려면 음수 — 예: -20%는 -20
     * @throws IllegalArgumentException -100 이하면
     */
    static SessionPriceAdjustment percent(int percent) {
        return percent == 0 ? none() : new Rate(percent);
    }

    /** {@link #percent}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    static boolean isValidPercent(int percent) {
        return percent > Rate.MINIMUM_EXCLUSIVE;
    }

    /** 조정의 종류. */
    SessionPriceAdjustmentKind kind();

    /** 조정 없음. */
    record None() implements SessionPriceAdjustment {

        private static final None INSTANCE = new None();

        @Override
        public SessionPriceAdjustmentKind kind() {
            return SessionPriceAdjustmentKind.NONE;
        }
    }

    /**
     * 금액 조정.
     *
     * @param won 더할 금액(원), 0이 아님. 빼면 음수
     */
    record Amount(long won) implements SessionPriceAdjustment {

        public Amount {
            if (won == 0) {
                throw new IllegalArgumentException("금액 조정이 0원입니다 — 조정 없음으로 둡니다");
            }
        }

        @Override
        public SessionPriceAdjustmentKind kind() {
            return SessionPriceAdjustmentKind.AMOUNT;
        }
    }

    /**
     * 퍼센트 조정.
     *
     * @param percent 더할 퍼센트, 0이 아니고 -100보다 크다. 빼면 음수
     */
    record Rate(int percent) implements SessionPriceAdjustment {

        private static final int MINIMUM_EXCLUSIVE = -100;

        public Rate {
            if (percent == 0) {
                throw new IllegalArgumentException("퍼센트 조정이 0%입니다 — 조정 없음으로 둡니다");
            }
            if (percent <= MINIMUM_EXCLUSIVE) {
                throw new IllegalArgumentException("퍼센트 조정은 -100%보다 커야 합니다: " + percent + "%");
            }
        }

        @Override
        public SessionPriceAdjustmentKind kind() {
            return SessionPriceAdjustmentKind.PERCENT;
        }
    }
}
