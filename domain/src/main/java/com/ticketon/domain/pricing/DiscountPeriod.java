package com.ticketon.domain.pricing;

import java.time.Instant;
import java.util.Objects;

/**
 * 할인 기간 — 조기예매의 기간(docs/05 "조기예매 — 종료 조건"), 쿠폰의 유효 기간 "시작~종료"(docs/05 "쿠폰").
 * <p>
 * <b>시작 순간부터 끝 순간 직전까지</b>다({@link #contains}) — 끝 순간에는 이미 끝났다. 그래서 1차가 3/1 0시에 끝나고 2차가 3/1 0시에 시작하면
 * 겹치지 않는다({@link #overlaps}). 날짜로 받은 기간("3/1까지")을 어느 순간으로 바꿀지는 받는 쪽이 정한다(그날 끝까지면
 * {@code KoreanTime.endOfDay}).
 * <p>
 * 확인 필요 (#49) — 시작과 끝을 모두 받는다. 문서는 쿠폰에 "시작~종료"를 적었고, 조기예매에는 "기간"만 적었다. 조기예매도 1차 · 2차의 기간 겹침을
 * 경고하려면(docs/05) 시작이 있어야 한다.
 *
 * @param startsAt 시작 순간. 이 순간부터 들어간다
 * @param endsAt   끝 순간. 이 순간에는 이미 끝났다. 시작보다 뒤
 */
public record DiscountPeriod(Instant startsAt, Instant endsAt) {

    public DiscountPeriod {
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("할인 기간의 끝이 시작보다 뒤여야 합니다: " + startsAt + " ~ " + endsAt);
        }
    }

    public static DiscountPeriod of(Instant startsAt, Instant endsAt) {
        return new DiscountPeriod(startsAt, endsAt);
    }

    /** 그 순간이 기간 안인지 — 시작 순간은 들어가고 끝 순간은 들어가지 않는다. */
    public boolean contains(Instant moment) {
        Objects.requireNonNull(moment, "moment");
        return !moment.isBefore(startsAt) && moment.isBefore(endsAt);
    }

    /** 그 순간에 아직 시작하지 않았는지. */
    public boolean hasNotStartedAt(Instant moment) {
        Objects.requireNonNull(moment, "moment");
        return moment.isBefore(startsAt);
    }

    /** 그 순간에 이미 끝났는지 — 끝 순간부터다. */
    public boolean hasEndedAt(Instant moment) {
        Objects.requireNonNull(moment, "moment");
        return !moment.isBefore(endsAt);
    }

    /** 두 기간이 한 순간이라도 겹치는지. 한쪽의 끝 순간과 다른 쪽의 시작 순간이 같으면 겹치지 않는다. */
    public boolean overlaps(DiscountPeriod other) {
        Objects.requireNonNull(other, "other");
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }
}
