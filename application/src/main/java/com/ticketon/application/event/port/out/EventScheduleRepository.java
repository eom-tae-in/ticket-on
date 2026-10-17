package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.EventSchedule;
import java.time.Instant;
import java.util.Optional;

/**
 * 행사 일정 저장소 — 나가는 포트. 일정은 행사에 하나다(docs/02 "날짜와 회차").
 * <p>
 * 회차 방식 · 기본 운영 시간 · 날짜 · 회차를 <b>일정 하나로</b> 읽고 저장한다 — 같은 날짜에 같은 시각의 회차가 없는지, 종일형 날짜에 회차가
 * 하나인지는 행사 줄을 잠근 흐름이 일정 하나를 읽고 도메인이 본 뒤 통째로 저장한다. 회차 방식을 아직 고르지 않은 행사는 일정이 없다.
 * <p>
 * <b>지운 날짜 · 회차는 줄을 남긴다.</b> 일정에서 빠진 회차 · 날짜는 지운 시각을 적어 두고, 다시 읽을 때는 담지 않는다 — 취소 · 회수된 티켓이
 * 원래 회차(와 그 날짜)를 계속 가리킨다(docs/04, docs/14). 지운 날짜의 날짜 값과 지운 회차의 시작 시각은 새 것이 다시 쓸 수 있다.
 * <p>
 * <b>회차 id는 바뀌지 않는다.</b> 재고 · 티켓 · 입장 기록이 회차 id를 가리키므로 저장된 회차는 고친 칸만 바뀌고 줄이 다시 만들어지지 않는다 —
 * 회차를 다른 날짜로 옮겨도 회차 줄은 그대로이고 가리키는 날짜만 바뀐다.
 * <p>
 * <b>잠금 — 행사 줄 다음이다.</b> 일정을 읽고 고쳐 저장하는 흐름은 먼저 행사 줄을 FOR UPDATE로 잠근다({@link EventRepository#findByIdForUpdate}).
 * 같은 행사의 일정 저장은 행사 줄에서 차례로 처리되므로 일정의 줄은 따로 잠그지 않는다.
 * <p>
 * <b>일정은 여러 문장으로 읽는다.</b> 행사 줄을 잠그지 않고 읽는 조회는 트랜잭션 전체를 스냅숏 하나로 읽어야 한다(REPEATABLE READ) — 문장마다 새
 * 스냅숏을 잡으면(READ COMMITTED) 그 사이에 커밋된 편집을 반쯤만 읽어 일정을 되살리지 못한다(회차 없는 날짜, 일정에 없는 날짜를 가리키는 회차).
 */
public interface EventScheduleRepository {

    /**
     * 그 행사의 일정을 읽는다 — 지우지 않은 날짜(날짜 순)와 그 회차(시작 시각 순). 회차 방식을 아직 고르지 않았으면 비어 있다. 행사가 있는지는 보지
     * 않는다 — 부르는 쪽이 행사를 먼저 찾는다. 행사 줄을 잠근 뒤에 부르거나, 스냅숏 하나(REPEATABLE READ)인 트랜잭션에서 부른다.
     */
    Optional<EventSchedule> findByEventId(long eventId);

    /**
     * 일정을 저장한다 — 일정 줄(회차 방식 · 기본 운영 시간)은 없으면 넣고 있으면 지금 값으로 쓴다. 일정에서 빠진(지금 표에서 지우지 않은) 회차 ·
     * 날짜에 지운 시각을 적고, 새 날짜(id 없음)를 넣고, 저장된 회차는 지금 값(가리키는 날짜 · 시간 · 출연 · 가격 조정 · 취소 시각)으로, 저장된 날짜는
     * 덮어쓴 운영 시간을 지금 값으로 쓰고, 새 회차를 넣는다. 날짜 값은 바꾸지 않는다 — 날짜를 옮기면 도메인이 새 날짜를 만든다.
     *
     * @param schedule {@link #findByEventId}로 읽어 고친 일정, 또는 회차 방식을 처음 골라 새로 만든 일정
     * @param now      일정에서 빠진 회차 · 날짜에 적을 지운 시각
     * @return 새 날짜 · 회차에 id가 붙은 일정
     * @throws IllegalArgumentException 표에 없거나 지운 날짜 · 회차, 다른 행사의 것, 날짜 값이 바뀐 날짜를 저장하려 하면 — 아무것도 쓰지 않는다
     */
    EventSchedule save(EventSchedule schedule, Instant now);
}
