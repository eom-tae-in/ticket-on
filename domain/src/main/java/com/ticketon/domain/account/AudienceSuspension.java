package com.ticketon.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * 관객 이용 정지 한 번 — 사유, 정지한 시각, 해제한 시각.
 * <p>
 * 관리자가 걸고 풀며, 사유를 기록한다(docs/01 "관객 이용 정지"). 해제해도 지우지 않고 이력으로 남긴다.
 * 관리자의 사용자 찾기가 정지 여부와 사유 이력을 보여 준다(docs/13 "사용자 찾기").
 *
 * @param reason      정지 사유. 앞뒤 공백을 걷는다.
 * @param suspendedAt 정지한 시각
 * @param liftedAt    해제한 시각. 아직 정지 중이면 null.
 */
public record AudienceSuspension(String reason, Instant suspendedAt, Instant liftedAt) {

    public AudienceSuspension {
        Objects.requireNonNull(reason, "reason");
        reason = reason.strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("이용 정지 사유가 비어 있습니다");
        }
        Objects.requireNonNull(suspendedAt, "suspendedAt");
        if (liftedAt != null && liftedAt.isBefore(suspendedAt)) {
            throw new IllegalArgumentException(
                    "해제 시각(" + liftedAt + ")이 정지 시각(" + suspendedAt + ")보다 앞설 수 없습니다");
        }
    }

    /** 지금 거는 정지. */
    static AudienceSuspension startingAt(String reason, Instant now) {
        return new AudienceSuspension(reason, now, null);
    }

    public boolean isLifted() {
        return liftedAt != null;
    }

    /** 이 정지를 지금 해제한 것. 사유와 정지 시각은 그대로 남는다. */
    AudienceSuspension liftAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isLifted()) {
            throw new IllegalStateException("이미 해제한 정지입니다");
        }
        return new AudienceSuspension(reason, suspendedAt, now);
    }
}
