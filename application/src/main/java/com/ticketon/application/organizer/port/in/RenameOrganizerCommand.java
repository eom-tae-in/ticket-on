package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.OrganizerName;
import java.util.Objects;

/**
 * 주최자명 변경 입력.
 *
 * @param actingStaff 바꾸는 운영진과 주최자 — 그 주최자의 소유자여야 한다
 * @param newName     바꿀 이름 — 앞뒤 공백을 걷고 NFC로 맞춘 값
 */
public record RenameOrganizerCommand(ActingStaff actingStaff, OrganizerName newName) {

    public RenameOrganizerCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(newName, "newName");
    }
}
