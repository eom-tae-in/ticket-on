package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import java.util.Optional;

/**
 * 주최자 저장소 — 나가는 포트.
 * <p>
 * 새 주최자 신청을 받으면 그 주최자를 심사 중으로 만들어 저장한다(docs/14 "전체 관계도" — 주최자 상태: 심사 중 / 활동 / 닫힘 / 영구 정지).
 * 승인되면 활동으로 바꾼다. 주최자는 지우지 않는다 — 닫혀도 '닫힘'으로 남고(docs/11 "데이터 보관"), 신청이 반려돼도 심사 중으로 남는다.
 */
public interface OrganizerRepository {

    /**
     * 새 주최자를 저장하고, id가 붙은 주최자를 돌려준다.
     *
     * @param newOrganizer {@link Organizer#underReview}로 만든, id가 없는 주최자 — 공개 정보는 비어 있다
     */
    Organizer add(Organizer newOrganizer);

    Optional<Organizer> findById(long id);

    /**
     * 승인한 주최자의 상태(활동)를 저장한다. 표에서 아직 심사 중일 때만 바꾼다 — 읽은 뒤 다른 처리가 먼저 상태를 바꿨으면 덮어쓰지 않는다.
     *
     * @param approvedOrganizer {@link Organizer#approve}로 활동이 된 저장된 주최자
     * @throws ConflictException        표의 주최자가 이미 심사 중이 아니면({@code ORGANIZER_NOT_UNDER_REVIEW})
     * @throws IllegalArgumentException 저장 전 주최자이거나 활동 상태가 아니면
     */
    void saveApproval(Organizer approvedOrganizer);
}
