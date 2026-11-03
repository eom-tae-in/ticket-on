package com.ticketon.domain.booking;

import com.ticketon.domain.pricing.BundleProduct;
import java.util.List;
import java.util.Objects;

/**
 * 관객이 고른 회차 — 회차 하나, 또는 묶음권과 날짜마다 고른 회차(docs/06 "전체 흐름 — 회차 선택 (또는 날짜 · 묶음권)").
 * <ul>
 *   <li><b>회차 하나</b>({@link #session}) — 지정석이든 수량형이든 일반 예매는 회차 하나를 고른다. 좌석 재고는 회차마다 따로라 회차를 먼저
 *       고른다.</li>
 *   <li><b>묶음권</b>({@link #bundle}) — 묶음권 상품은 날짜를 가리키고, 관객이 날짜마다 회차 하나씩을 고른다. 페스티벌(날짜당 회차 1개)은 날짜가
 *       곧 회차고, 시간대를 나눈 전시는 날짜마다 시간대를 고른다(docs/05 "묶음권 (수량형 전용)").</li>
 * </ul>
 * 고른 회차가 그 행사의 일정에 있는지, 묶음권이면 날짜마다 하나씩인지는 판정이 일정과 맞춰 본다({@link BookingEligibilityJudge}).
 */
public sealed interface BookingSelection permits BookingSelection.SingleSession, BookingSelection.Bundle {

    /** 회차 하나. */
    static BookingSelection session(long sessionId) {
        return new SingleSession(sessionId);
    }

    /**
     * 묶음권과 날짜마다 고른 회차.
     *
     * @param chosenSessionIds 묶은 날짜마다 하나씩 고른 회차
     */
    static BookingSelection bundle(BundleProduct product, List<Long> chosenSessionIds) {
        return new Bundle(product, chosenSessionIds);
    }

    /** 고른 회차들. */
    List<Long> sessionIds();

    /**
     * 회차 하나.
     *
     * @param sessionId 고른 회차
     */
    record SingleSession(long sessionId) implements BookingSelection {

        @Override
        public List<Long> sessionIds() {
            return List.of(sessionId);
        }
    }

    /**
     * 묶음권과 날짜마다 고른 회차.
     *
     * @param product    고른 묶음권 상품
     * @param sessionIds 묶은 날짜마다 하나씩 고른 회차
     */
    record Bundle(BundleProduct product, List<Long> sessionIds) implements BookingSelection {

        public Bundle {
            Objects.requireNonNull(product, "product");
            sessionIds = List.copyOf(sessionIds);
        }
    }
}
