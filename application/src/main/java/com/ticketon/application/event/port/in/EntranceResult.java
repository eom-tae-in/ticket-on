package com.ticketon.application.event.port.in;

import java.util.Objects;

/**
 * 입구 한 줄 — 위저드 6단계의 입구 목록이 보이는 칸(docs/02 "입구 (게이트)"). 이름은 앞뒤 공백을 걷고 NFC로 맞춘 값이다.
 *
 * @param entranceId 입구 id
 * @param name       이름
 */
public record EntranceResult(long entranceId, String name) {

    public EntranceResult {
        Objects.requireNonNull(name, "name");
    }
}
