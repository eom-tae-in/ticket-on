package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.ZoneKind;
import java.util.Objects;

/**
 * 구역 한 줄 — 위저드 3단계의 구역 목록이 보이는 칸(docs/02 "구역", docs/05 "가격의 층위"). 이름은 앞뒤 공백을 걷고 NFC로 맞춘 값, 색은
 * {@code #RRGGBB} 대문자다.
 *
 * @param zoneId    구역 id
 * @param kind      종류 — 지정형 / 수량형
 * @param name      이름
 * @param color     색
 * @param basePrice 기본가(원)
 * @param capacity  수량형의 정원. 지정형이면 null
 */
public record ZoneResult(long zoneId, ZoneKind kind, String name, String color, long basePrice, Integer capacity) {

    public ZoneResult {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(color, "color");
    }
}
