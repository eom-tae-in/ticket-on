package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 입구 추가 — 들어오는 포트. 위저드 6단계에서 행사의 입구 목록에 입구를 더한다. 판매를 시작한 뒤에도, 판매가 끝난 뒤에도 행사가 끝날 때까지 한다 —
 * 18:10에 정문 줄이 길어 "옆문"을 더한다(docs/04 "판매 종료는 공연이 끝난 때가 아닙니다"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}).
 * <p>
 * 수정 범위 확인 · 진행 단계 · 마지막 수정 시각은 {@link Event#addEntrance}가 정한다. 운영진 조작 기록에 남는다.
 */
public interface AddEntranceUseCase {

    /**
     * @return 더한 입구
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    같은 이름의 입구가 있거나({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN}), 지난 행사면
     *                                   ({@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    EntranceResult add(AddEntranceCommand command);
}
