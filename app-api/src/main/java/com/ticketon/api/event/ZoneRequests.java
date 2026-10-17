package com.ticketon.api.event;

import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;

/** 구역 요청의 칸을 구역 한 줄의 칸(도메인 값)으로 바꾼다 — 구역 추가 · 수정이 같다. 검증을 통과한 칸만 넘어온다. */
final class ZoneRequests {

    private ZoneRequests() {
    }

    static ZoneSettings settings(String name, String color, long basePrice, Integer capacity) {
        return new ZoneSettings(ZoneName.of(name), ZoneColor.of(color), ZoneBasePrice.of(basePrice),
                capacity == null ? null : ZoneCapacity.of(capacity));
    }
}
