package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.FloorPlan;
import java.time.Instant;

/**
 * 행사 도면 저장소 — 나가는 포트. 도면은 행사에 하나다(docs/02 "두 개의 축").
 * <p>
 * 지금은 구역과 무대 위치를 저장한다. 블록 · 좌석은 배치도 편집(#43)이 더한다 — 그 전에는 블록이 있는 도면을 저장하지 않는다.
 * <p>
 * <b>지운 구역은 줄을 남긴다.</b> 도면에서 빠진 구역은 지운 시각을 적어 두고, 다시 읽을 때는 담지 않는다 — 취소 · 회수된 티켓이 원래 구역을 계속
 * 가리킨다(docs/14). 지운 구역의 이름은 새 구역이 다시 쓸 수 있다.
 * <p>
 * <b>잠금 — 행사 줄 다음이다.</b> 도면을 읽고 고쳐 저장하는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다({@link EventRepository#findByIdForUpdate}).
 * 같은 행사의 도면 저장은 행사 줄에서 차례로 처리되므로 도면의 줄은 따로 잠그지 않는다.
 */
public interface FloorPlanRepository {

    /**
     * 그 행사의 도면을 읽는다 — 지우지 않은 구역(더한 순서)과 무대 위치. 구역이 없으면 빈 도면이다. 행사가 있는지는 보지 않는다 — 부르는 쪽이 행사를
     * 먼저 찾는다.
     */
    FloorPlan findByEventId(long eventId);

    /**
     * 도면을 저장한다 — 도면에서 빠진(지금 표에서 지우지 않은) 구역에 지운 시각을 적고, 저장된 구역은 지금 값으로 쓰고, 새 구역(id 없음)은 넣는다.
     * 무대 위치를 정했으면 지금 값으로 쓴다. 구역 종류는 바꾸지 않는다.
     *
     * @param floorPlan {@link #findByEventId}로 읽어 고친 도면
     * @param now       도면에서 빠진 구역에 적을 지운 시각
     * @return 새 구역에 id가 붙은 도면 — 구역은 더한 순서
     * @throws IllegalArgumentException 블록이 있는 도면이면(#43 전), 표에 없거나 지운 구역 · 다른 행사의 구역 · 종류가 바뀐 구역을 저장하려 하면
     */
    FloorPlan save(FloorPlan floorPlan, Instant now);
}
