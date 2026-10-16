package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.Event;
import java.util.Optional;

/**
 * 행사 저장소 — 나가는 포트.
 * <p>
 * 행사 한 줄(기본 정보 · 공개 수준 · 예매 설정 · 상태 · 공개 주소 · 작성 진행 단계 · 마지막 수정 시각)과 그 취소 규정 구간 · 판매 중단 이력을
 * 함께 저장하고 함께 읽는다({@link Event}). 행사는 1단계 기본 정보와 함께 처음 저장된다 — 빈 초안은 없다(docs/03 "임시저장은 자동으로").
 * <p>
 * <b>지운 행사는 없는 행사다.</b> 행사 삭제(#66)는 줄을 지우지 않고 삭제 시각을 남길 수 있는데, 그런 행사는 찾지 않는다(docs/01 "작성 중 ·
 * 삭제된 행사 — 없는 행사").
 * <p>
 * 날짜 · 회차는 일정, 도면은 도면이 따로 저장한다. 포스터는 포스터 업로드(#40)가 저장한다 — 그 전에는 포스터가 있는 행사를 저장하지 않는다.
 */
public interface EventRepository {

    /**
     * 새 행사를 저장하고, id가 붙은 행사를 돌려준다.
     *
     * @param newEvent {@link Event#create}로 만든, id가 없는 행사
     * @throws IllegalArgumentException 이미 저장된 행사이거나, 고른 정식 카테고리가 카테고리 표에 없으면
     */
    Event add(Event newEvent);

    /** 지우지 않은 행사를 읽는다. 없거나 지운 행사면 비어 있다. */
    Optional<Event> findById(long id);

    /**
     * 지우지 않은 행사를 읽고, 이 트랜잭션이 끝날 때까지 그 줄을 다른 변경에서 잠근다(FOR UPDATE) — 위저드 단계처럼 읽고 고쳐 저장하는 일에 쓴다.
     * <p>
     * 두 운영진(또는 한 사람의 두 탭)이 거의 동시에 같은 행사의 단계를 저장하면 뒤의 저장은 앞의 저장이 끝날 때까지 기다렸다가 바뀐 값을 읽는다 —
     * 서로 다른 단계를 저장해도 한쪽 단계가 묵은 값으로 덮이지 않고, 같은 값을 두 번 저장하면 뒤의 저장은 바뀐 것이 없어 기록도 남지 않는다.
     * <p>
     * <b>잠금 순서 — 운영진 계정 줄 · 주최자 줄 다음이다</b>(organizer 서비스의 {@code StaffAccountLocks} 표). 이 줄을 쥔 채 조작 기록을 넣으면
     * 조작한 사람의 계정 줄과 주최자 줄에 외래 키 확인 잠금이 걸리므로, 부르기 전에 그 두 줄을 먼저 잡는다.
     */
    Optional<Event> findByIdForUpdate(long id);

    /**
     * 고친 행사를 저장한다 — 행사 줄의 칸을 모두 지금 값으로 쓰고, 취소 규정 구간은 통째로 바꾸고, 판매 중단 이력은 새로 생긴 중단을 넣고 끝난
     * 중단의 끝난 시각을 적는다. {@link #findByIdForUpdate}로 잠그고 읽은 뒤 부른다.
     *
     * @throws IllegalArgumentException 저장 전 행사이거나 표에 없는(지운) 행사이면, 고른 정식 카테고리가 카테고리 표에 없으면
     * @throws IllegalStateException    포스터가 있는 행사이면 — 포스터 칸은 포스터 업로드(#40)가 더한다
     */
    void save(Event event);
}
