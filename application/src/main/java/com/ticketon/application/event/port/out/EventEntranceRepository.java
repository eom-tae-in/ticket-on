package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.EventEntrances;
import java.time.Instant;

/**
 * 행사 입구 목록 저장소 — 나가는 포트. 입구 목록은 도면에 속하지만 판매가 끝난 뒤에도 고쳐 따로 둔다({@link EventEntrances}).
 * <p>
 * <b>지운 입구는 줄을 남긴다.</b> 목록에서 빠진 입구는 지운 시각을 적어 두고, 다시 읽을 때는 담지 않는다 — 그 입구를 가리키는 입장 기록이
 * 남는다(docs/14 "입장 기록 — 티켓 + 회차 + 입구"). 지운 입구의 이름은 새 입구가 다시 쓸 수 있다.
 * <p>
 * <b>잠금 — 행사 줄 다음이다.</b> 입구 목록을 읽고 고쳐 저장하는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다
 * ({@link EventRepository#findByIdForUpdate}). 같은 행사의 입구 저장은 행사 줄에서 차례로 처리되므로 입구 줄은 따로 잠그지 않는다.
 */
public interface EventEntranceRepository {

    /** 그 행사의 입구 목록을 읽는다 — 지우지 않은 입구, 더한 순서. 없으면 빈 목록이다. 행사가 있는지는 보지 않는다. */
    EventEntrances findByEventId(long eventId);

    /**
     * 입구 목록을 저장한다 — 목록에서 빠진(지금 표에서 지우지 않은) 입구에 지운 시각을 적고, 저장된 입구는 지금 이름으로 쓰고, 새 입구(id 없음)는
     * 넣는다.
     *
     * @param entrances {@link #findByEventId}로 읽어 고친 입구 목록
     * @param now       목록에서 빠진 입구에 적을 지운 시각
     * @return 새 입구에 id가 붙은 입구 목록 — 더한 순서
     * @throws IllegalArgumentException 표에 없거나 지운 입구 · 다른 행사의 입구를 저장하려 하면
     */
    EventEntrances save(EventEntrances entrances, Instant now);
}
