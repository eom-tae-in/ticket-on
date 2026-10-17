package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.EntranceName;
import java.util.Objects;

/**
 * 입구 이름 변경 입력.
 *
 * @param actingStaff 바꾸는 운영진과 주최자
 * @param eventId     행사 id
 * @param entranceId  입구 id
 * @param name        새 이름
 */
public record RenameEntranceCommand(ActingStaff actingStaff, long eventId, long entranceId, EntranceName name) {

    public RenameEntranceCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(name, "name");
    }
}
