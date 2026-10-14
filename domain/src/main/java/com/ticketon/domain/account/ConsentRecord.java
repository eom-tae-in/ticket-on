package com.ticketon.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * 동의 기록 한 줄 — 어느 항목에 동의했는지(또는 하지 않았는지)와 그 일시.
 * <p>
 * 동의를 받았다는 근거로 남긴다. 가입 때는 네 항목을 모두 남긴다. 동의하지 않은 마케팅 수신도 '동의하지 않음'으로 남는다.
 * 마케팅 수신 동의를 나중에 바꾸면 기록을 고치지 않고 새 줄을 더한다(#18). 그래서 한 항목의 지금 상태는 그 항목의 가장 최근 줄이다.
 *
 * @param item       동의 항목
 * @param agreed     동의했는지
 * @param recordedAt 동의하거나 동의하지 않은 일시
 */
public record ConsentRecord(ConsentItem item, boolean agreed, Instant recordedAt) {

    public ConsentRecord {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
