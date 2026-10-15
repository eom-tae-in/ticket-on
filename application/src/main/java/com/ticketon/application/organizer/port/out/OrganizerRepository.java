package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.organizer.Organizer;

/**
 * 주최자 저장소 — 나가는 포트.
 * <p>
 * 새 주최자 신청을 받으면 그 주최자를 심사 중으로 만들어 저장한다(docs/14 "전체 관계도" — 주최자 상태: 심사 중 / 활동 / 닫힘 / 영구 정지).
 * 주최자는 지우지 않는다 — 닫혀도 '닫힘'으로 남는다(docs/11 "데이터 보관").
 */
public interface OrganizerRepository {

    /**
     * 새 주최자를 저장하고, id가 붙은 주최자를 돌려준다.
     *
     * @param newOrganizer {@link Organizer#underReview}로 만든, id가 없는 주최자 — 공개 정보는 비어 있다
     */
    Organizer add(Organizer newOrganizer);
}
