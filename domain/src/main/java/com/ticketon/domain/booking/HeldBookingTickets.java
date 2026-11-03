package com.ticketon.domain.booking;

import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.ticket.Ticket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관객이 이 행사에서 이미 가진 예매 티켓 가운데 매수 제한 · 회차 선택 제약이 세는 것 — 티켓이 가리키는 회차 하나하나를 센다.
 * <ul>
 *   <li><b>유효한 예매 티켓만</b> 센다. 취소된 티켓은 세지 않는다. <b>초대권 티켓은 매수 제한에 포함되지 않는다</b> — 주최자가 의도해서 준 것이라
 *       제한할 까닭이 없다(docs/06 "집계 기준", docs/09 "발권 후"). 초대권은 계정에 붙지도 않는다.</li>
 *   <li><b>묶음권 1장은 가리키는 날짜마다 1매</b>다 — 3일권 1장은 5/3 · 5/4 · 5/5에 1매씩, 행사 전체로는 3매다(docs/05 "매수 제한 계산"). 일반 티켓은
 *       회차 하나라 그 날짜에 1매다. 매수 제한은 "그 날 몇 자리를 차지하는가"를 보는 장치라 그대로 센다.</li>
 *   <li>확인 필요 (#223) — <b>취소된 회차는 세지 않는다.</b> 묶음권에서 주최자가 하루를 취소하면 티켓은 그대로 두고 그날 몫을 환불하므로(docs/08
 *       "묶음권 중 하루가 취소되면") 티켓은 유효한 채 취소된 회차를 가리킨다. 그날은 자리가 없어 차지하는 것이 없다. 반면 <b>출연자가 바뀐 날을
 *       포기한 회차는 센다</b> — 티켓이 그 회차를 계속 가리키고(docs/04 "묶음권은 바뀐 날만 포기합니다") 그 자리가 풀리는지 문서가 말하지 않아,
 *       "이미 가진 유효 예매 티켓"을 글자 그대로 따랐다.</li>
 * </ul>
 * 이미 산 표는 그대로 두고 세기만 한다 — 판매 중에 매수 제한을 낮춰도 이미 산 표는 그대로이고, 1인 총 매수에는 이미 산 표도 센다(docs/04 "예매가
 * 열린 뒤(판매 중) 수정 범위").
 */
final class HeldBookingTickets {

    private final List<HeldSession> heldSessions;

    private HeldBookingTickets(List<HeldSession> heldSessions) {
        this.heldSessions = List.copyOf(heldSessions);
    }

    /**
     * 관객이 가진 예매 티켓에서 셀 것을 고른다.
     *
     * @param tickets  관객이 이 행사에서 가진 예매 티켓 — 취소된 티켓 · 초대권 티켓이 섞여 있어도 걸러 낸다
     * @param schedule 그 행사의 일정 — 회차가 어느 날짜인지 읽는다
     * @throws IllegalArgumentException 유효한 예매 티켓이 이 일정에 없는 회차를 가리키면 — 다른 행사의 티켓이다
     */
    static HeldBookingTickets of(List<Ticket> tickets, EventSchedule schedule) {
        Objects.requireNonNull(tickets, "tickets");
        Objects.requireNonNull(schedule, "schedule");
        Set<Long> scheduledSessionIds = schedule.sessions().stream()
                .filter(session -> session.id().isPresent())
                .map(session -> session.id().getAsLong())
                .collect(Collectors.toSet());
        List<HeldSession> heldSessions = new ArrayList<>();
        for (Ticket ticket : tickets) {
            if (!ticket.parent().isBooking() || ticket.isCancelled()) {
                continue;
            }
            for (long sessionId : ticket.sessionIds()) {
                if (!scheduledSessionIds.contains(sessionId)) {
                    throw new IllegalArgumentException("이 행사의 티켓이 아닙니다: 행사 " + schedule.eventId() + ", 티켓 " + ticket.number()
                            + ", 회차 " + sessionId);
                }
                EventSession session = schedule.session(sessionId);
                if (!session.isCancelled()) {
                    heldSessions.add(new HeldSession(sessionId, schedule.dateOf(sessionId).id().orElseThrow()));
                }
            }
        }
        return new HeldBookingTickets(heldSessions);
    }

    /** 가진 티켓이 가리키는 회차들(겹치지 않게). */
    Set<Long> sessionIds() {
        return heldSessions.stream().map(HeldSession::sessionId).collect(Collectors.toUnmodifiableSet());
    }

    /** 그 날짜에서 가진 티켓이 가리키는 회차들(겹치지 않게). */
    Set<Long> sessionIdsOn(long dateId) {
        return heldSessions.stream()
                .filter(held -> held.dateId() == dateId)
                .map(HeldSession::sessionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 티켓 한 장이 가리키는 회차 하나 — 그 날짜에 1매. */
    private record HeldSession(long sessionId, long dateId) {
    }
}
