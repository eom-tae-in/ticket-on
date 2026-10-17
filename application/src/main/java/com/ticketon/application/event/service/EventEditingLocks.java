package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;

/**
 * 행사를 만들거나 고치는 흐름(행사 만들기, 위저드 단계 저장, 포스터 올리기)의 시작 — 이 흐름들이 같은 잠금 순서와 같은 재확인을 쓰게 한곳에 둔다. 잠금 순서는
 * 운영진 계정 줄 → 주최자 줄 → 행사 줄이다(organizer 서비스의 {@link StaffAccountLocks} 표).
 * <ol>
 *   <li>권한을 확인한다 — 권한이 없는 요청은 아무 줄도 잠그지 않고 막는다.</li>
 *   <li>조작하는 사람의 계정 줄에 외래 키 확인 잠금(FOR KEY SHARE)을 건다 — 조작 기록이 그 줄을 가리킨다. 그 사람의 소속을 바꾸는 흐름(내보내기,
 *       나가기, 소유자 넘기기 등 — 계정 줄 FOR UPDATE)이 먼저 잡았으면 그 흐름이 끝날 때까지 기다린다.</li>
 *   <li>주최자 줄에 외래 키 확인 잠금을 건다 — 새 행사 줄과 조작 기록이 그 줄을 가리킨다. 행사 줄을 잡기 전에 걸어, 행사 줄을 쥔 채 주최자 줄을
 *       기다리지 않는다. 주최자 줄을 FOR UPDATE로 고치는 흐름(공개 정보 편집 · 주최자명 변경 등)이 먼저 잡았으면 그 흐름이 끝날 때까지 기다린다.
 *       외래 키 확인 잠금끼리는 부딪히지 않아 같은 주최자의 다른 행사를 고치는 흐름은 막지 않는다.</li>
 *   <li><b>잠근 뒤 명단을 다시 읽어 권한을 다시 확인한다</b> — 기다리는 사이 그 사람이 내보내졌거나 나갔으면 처음 확인과 같은 규칙(403)으로 막아,
 *       소속이 끝난 뒤의 조작이 처리 · 기록되지 않는다. 두 줄을 쥔 동안에는 그 사람의 소속이 바뀌지 않는다.</li>
 *   <li>(고칠 때) 행사 줄을 FOR UPDATE로 잠그고 읽는다 — 같은 행사의 단계 저장 · 포스터 올리기끼리 차례로 처리된다. 다른 주최자의 행사는 없는
 *       것으로 다룬다.</li>
 * </ol>
 */
final class EventEditingLocks {

    private EventEditingLocks() {
    }

    /**
     * 권한 확인 → 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인.
     *
     * @return 잠근 뒤 다시 확인한 권한 — 조작 기록에 이 권한(그때의 역할)을 남긴다
     * @throws com.ticketon.domain.error.PermissionDeniedException 처음 확인이나 잠근 뒤의 다시 확인에서 막히면
     */
    static StaffPermissionResult lockOrganizer(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            StaffAccountRepository staffAccountRepository, OrganizerRepository organizerRepository,
            CheckStaffPermissionQuery query) {
        StaffPermissionResult checked = checkStaffPermissionUseCase.check(query);
        long staffAccountId = checked.staffAccountId();
        long organizerId = checked.organizerId();
        if (StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, staffAccountId).get(staffAccountId) == null) {
            throw new IllegalStateException("권한을 확인한 운영진 계정이 없습니다: " + staffAccountId);
        }
        organizerRepository.findByIdForKeyShare(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        return checkStaffPermissionUseCase.check(query);
    }

    /**
     * 그 주최자의 행사 줄을 FOR UPDATE로 잠그고 읽는다 — {@link #lockOrganizer} 다음에 부른다.
     *
     * @throws NotFoundException 없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     */
    static Event lockEvent(EventRepository eventRepository, StaffPermissionResult permission, long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .filter(event -> permission.covers(event.organizerId()))
                .orElseThrow(() -> new NotFoundException(EventErrorCode.EVENT_NOT_FOUND,
                        "행사 " + eventId + ", 주최자 " + permission.organizerId()));
    }
}
