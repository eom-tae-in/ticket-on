package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import java.util.Optional;

/**
 * 주최자 저장소 — 나가는 포트.
 * <p>
 * 새 주최자 신청을 받으면 그 주최자를 심사 중으로 만들어 저장한다(docs/14 "전체 관계도" — 주최자 상태: 심사 중 / 활동 / 닫힘 / 영구 정지).
 * 승인되면 활동으로 바꾼다. 주최자는 지우지 않는다 — 닫혀도 '닫힘'으로 남고(docs/11 "데이터 보관"), 신청이 반려돼도 심사 중으로 남는다.
 * 공개 정보(소개 · SNS 링크 · 로고 열쇠)도 주최자와 함께 저장한다. 로고 파일 자체는 파일 저장소에 있고 여기에는 그 열쇠만 둔다.
 */
public interface OrganizerRepository {

    /**
     * 새 주최자를 저장하고, id가 붙은 주최자를 돌려준다.
     *
     * @param newOrganizer {@link Organizer#underReview}로 만든, id가 없는 주최자 — 공개 정보는 비어 있다
     */
    Organizer add(Organizer newOrganizer);

    /** 공개 정보(소개 · SNS 링크 · 로고)까지 함께 읽는다. */
    Optional<Organizer> findById(long id);

    /**
     * 주최자를 읽고, 이 트랜잭션이 끝날 때까지 그 줄을 다른 변경에서 잠근다 — 공개 정보처럼 읽고 고쳐 저장하는 일에 쓴다.
     * <p>
     * 두 운영진이 거의 동시에 로고를 바꾸면 뒤의 요청은 앞의 요청이 끝날 때까지 기다렸다가 바뀐 값을 읽는다. 둘이 같은 "바꾸기 전 로고"를 읽어
     * 먼저 올린 로고 파일이 아무도 가리키지 않은 채 남는 일이 없게 한다.
     */
    Optional<Organizer> findByIdForUpdate(long id);

    /**
     * 승인한 주최자의 상태(활동)를 저장한다. 표에서 아직 심사 중일 때만 바꾼다 — 읽은 뒤 다른 처리가 먼저 상태를 바꿨으면 덮어쓰지 않는다.
     *
     * @param approvedOrganizer {@link Organizer#approve}로 활동이 된 저장된 주최자
     * @throws ConflictException        표의 주최자가 이미 심사 중이 아니면({@code ORGANIZER_NOT_UNDER_REVIEW})
     * @throws IllegalArgumentException 저장 전 주최자이거나 활동 상태가 아니면
     */
    void saveApproval(Organizer approvedOrganizer);

    /**
     * 공개 정보(소개 · SNS 링크 · 로고)를 저장한다. 이름 · 상태는 건드리지 않는다. SNS 링크는 적은 순서대로 통째로 바꾼다.
     *
     * @param organizer 공개 정보를 바꾼 저장된 주최자({@link Organizer#changeIntroductionAndSnsLinks}, {@link Organizer#changeLogo},
     *                  {@link Organizer#removeLogo})
     * @throws IllegalArgumentException 저장 전 주최자이거나 표에 없는 주최자이면
     */
    void savePublicProfile(Organizer organizer);
}
