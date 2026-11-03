package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import java.time.Instant;
import java.util.Objects;

/**
 * 변경 이력의 첫 버전 쌓기 입력.
 *
 * @param permission  판매를 시작한 운영진의 권한 — 잠근 뒤 다시 확인한 것. 첫 버전의 "바꾼 사람"이다
 * @param eventId     판매를 시작한 행사
 * @param publishedAt 판매 대기로 넘어간 순간 — 첫 버전의 유효 시작. 판매 시작 흐름의 지금 시각 그대로
 */
public record RecordChangeHistoryFirstVersionCommand(StaffPermissionResult permission, long eventId, Instant publishedAt) {

    public RecordChangeHistoryFirstVersionCommand {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
