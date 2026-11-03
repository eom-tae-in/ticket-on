package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 행사 복제 — 들어오는 포트. 정기공연 주최자가 매번 처음부터 만들지 않게, 주최자의 행사를 복제해 작성 중 행사를 새로 만든다(docs/04 "행사
 * 복제"). 종료된 행사든 진행 중인 행사든 상태와 상관없이 복제한다.
 * <p>
 * 복제본은 작성 중이고 제목은 "○○공연 (복사본)"이다. 나머지 기본 정보 · 예매 설정 · 취소 규정 조정과 도면(구역 · 기본가 · 블록 · 좌석 · 속성 ·
 * 무대) · 입구 목록을 가져오고, 날짜 · 회차 · 공개 수준(공개로 시작) · 예매 오픈 시각 · 포스터는 가져오지 않는다. 구역 · 블록 · 좌석 · 입구는 새
 * id로 복사되어 그 뒤로 원본과 아무 관계가 없다 — 복제본을 고쳐도 원본은 그대로다(docs/02 "도면은 행사마다 따로 갖습니다").
 * <p>
 * 새 행사를 만드는 일이라 행사 만들기와 같은 권한이다 — 활동 중인 주최자의 운영진 누구나({@link StaffAction#CREATE_EVENT}, docs/01 "권한 두
 * 가지"). 확인 필요 (#223) — 문서가 복제의 권한을 따로 정하지 않아 행사 만들기로 보았다. 운영진 조작 기록에 남는다. 정지 1단계의 신규 개설 차단은
 * 정지 기능(#122)이 더한다.
 */
public interface DuplicateEventUseCase {

    /**
     * @return 복제본의 위저드 값
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나(없는 주최자도) 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     */
    EventWizardResult duplicate(DuplicateEventCommand command);
}
