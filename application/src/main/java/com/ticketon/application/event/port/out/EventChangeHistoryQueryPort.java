package com.ticketon.application.event.port.out;

import com.ticketon.application.event.query.EventChangeHistoryEntry;
import java.util.List;

/**
 * 행사 변경 이력 조회 — 나가는 포트. 관리자가 신고를 확인할 때 관객이 무엇을 보고 샀는지 예매 시각과 대조해 본다(docs/03 "주최자가 알리지 않아
 * 관객이 신고하면 관리자가 이 이력으로 확인", docs/04 "주최자가 안 알리면 — 관객 신고 → 관리자가 변경 이력으로 확인"). 읽기 전용이라 도메인 객체를
 * 만들지 않고 화면에 쓸 칸만 돌려준다. 이 포트를 부르는 관리자 유스케이스 · API는 관리자 기능이 더한다.
 */
public interface EventChangeHistoryQueryPort {

    /**
     * 그 행사의 변경 이력 — 유효 시작 차례(같으면 쌓은 차례). 지운 행사 · 회차의 이력도 담는다(보관 기간 안이면 신고 확인에 필요하다). 보관 기간이
     * 지나 지운 이력은 없다.
     * <p>
     * 이력과 회차 · 날짜를 한 문장으로 읽는다 — 행사 줄을 잠그지 않으므로 여러 문장으로 나눠 읽으면 그 사이에 쌓인 버전이 섞일 수 있다.
     */
    List<EventChangeHistoryEntry> findByEventId(long eventId);
}
