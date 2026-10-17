package com.ticketon.domain.ticket;

import java.time.Instant;
import java.util.Objects;

/**
 * 재발행으로 바뀐 옛 QR 열쇠와 바뀐 시각 — 재발행 이력에 보관한다(docs/07 "QR 재발행 — 처리", docs/14 "QR 재발행 기록 → 티켓 + 옛 QR 열쇠").
 * <p>
 * <b>옛 열쇠를 보관하는 이유</b> — 관객에게 QR을 미리 캡처해 두라고 안내하므로 재발행 뒤 옛 캡처를 내밀 수 있다. 옛 열쇠를 모르면 검표 화면이
 * "확인할 수 없는 QR"을 띄워 위조처럼 보인다. 알면 "재발행으로 바뀐 QR입니다"로 안내한다. 이력 저장은 #57이 한다.
 *
 * @param key        바뀌기 전 열쇠
 * @param replacedAt 바뀐 시각
 */
public record PreviousQrKey(QrKey key, Instant replacedAt) {

    public PreviousQrKey {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(replacedAt, "replacedAt");
    }
}
