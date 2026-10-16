package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.RequestedOrganizerChange;
import java.util.Objects;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 입력. 새 값은 민감해 {@link #toString}에는 종류만 싣는다.
 *
 * @param actingStaff     요청하는 운영진과 주최자 — 그 주최자의 소유자여야 한다
 * @param requestedChange 새 정산 계좌 또는 새 사업자 정보
 */
public record RequestOrganizerChangeCommand(ActingStaff actingStaff, RequestedOrganizerChange requestedChange) {

    public RequestOrganizerChangeCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(requestedChange, "requestedChange");
    }

    @Override
    public String toString() {
        return "RequestOrganizerChangeCommand[actingStaff=" + actingStaff + ", kind=" + requestedChange.kind() + ", ****]";
    }
}
