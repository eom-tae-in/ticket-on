package com.ticketon.domain.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 기준 시각보다 앞서는 시간 — "회차 시작 1시간 전"의 "1시간". 예매 종료와 검표 시작을 정한다(docs/06 "예매 종료", docs/03 "검표 시작 시각").
 * <ul>
 *   <li><b>예매 종료</b> — 회차형은 회차 시작, 종일형은 마지막 입장보다 이만큼 앞에서 예매를 닫는다.</li>
 *   <li><b>검표 시작</b> — 회차형은 회차 시작, 종일형은 운영 시작보다 이만큼 앞에서 검표를 연다.</li>
 * </ul>
 * 어느 시각을 기준으로 삼는지(회차 방식에 따라)는 회차가 정하고, 이 값은 기준 시각에서 빼기만 한다({@link #before}).
 * <p>
 * 0이면 기준 시각 그대로다 — "회차 시작 시각까지 예매"처럼. 기준 시각보다 늦게(음수) 둘 수는 없다: 예매 종료는 "시작 N시간 전", 검표 시작은
 * "시작 N시간 전부터"라 기준 시각을 넘지 않는다. 문서에 단위와 상한이 없어 둘 다 정하지 않았다.
 *
 * @param duration 앞서는 시간 (0 이상)
 */
public record LeadTime(Duration duration) {

    public LeadTime {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("기준 시각보다 앞서는 시간은 0 이상이어야 합니다: " + duration);
        }
    }

    public static LeadTime of(Duration duration) {
        return new LeadTime(duration);
    }

    /** 기준 시각에서 이만큼 앞선 순간. 예: 19:00 회차, 1시간 → 18:00. */
    public Instant before(Instant reference) {
        Objects.requireNonNull(reference, "reference");
        return reference.minus(duration);
    }
}
