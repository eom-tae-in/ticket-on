package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BlockName;
import java.util.Objects;

/**
 * 블록 이름 변경 입력.
 *
 * @param actingStaff 바꾸는 운영진과 주최자
 * @param eventId     행사 id
 * @param blockId     블록 id
 * @param name        새 이름. 비웠으면 null — "블록 N"을 붙인다
 */
public record RenameSeatBlockCommand(ActingStaff actingStaff, long eventId, long blockId, BlockName name) {

    public RenameSeatBlockCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
