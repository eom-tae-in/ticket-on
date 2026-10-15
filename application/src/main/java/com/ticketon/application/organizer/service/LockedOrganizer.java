package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import java.util.Objects;

/**
 * 주최자 줄을 잠그고 고치는 흐름(공개 정보 편집, 로고 올리기 · 지우기, 주최자명 변경, 정산 계좌 · 사업자 정보 변경 요청)의 시작 — 이 흐름들이
 * 같은 잠금 순서와 같은 재확인을 쓰게 한곳에 둔다.
 * <ol>
 *   <li>권한을 확인한다 — 권한이 없는 요청은 아무 줄도 잠그지 않고 막는다.</li>
 *   <li>잠금 순서대로 조작하는 사람의 계정 줄(외래 키 확인 잠금) → 주최자 줄(FOR UPDATE)을 잡는다({@link StaffAccountLocks}). 그 계정 줄을
 *       먼저 잠근 흐름(소유자 넘기기, 내보내기, 나가기, 소유자 이전 신청 승인, 운영진 로그인 탈퇴)이 있으면 그 흐름이 끝날 때까지 계정 줄에서
 *       기다린다.</li>
 *   <li><b>잠근 뒤 명단을 다시 읽어 권한을 다시 확인한다</b> — 소유자 넘기기의 "잠근 뒤 다시 읽은 명단"과 같다. 기다리는 사이 그 사람이
 *       내보내졌거나 나갔으면 {@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, 소유자를 넘겨 운영자가 됐는데 소유자만 하는 일이면
 *       {@link OrganizerErrorCode#OWNER_ONLY_ACTION}으로, 처음 확인과 같은 규칙으로 막는다. 소속이 끝났거나 역할이 바뀐 뒤의 조작이 처리 ·
 *       기록되지 않는다.</li>
 * </ol>
 * 두 줄을 쥔 동안에는 그 사람의 소속과 주최자 상태가 바뀌지 않는다 — 소속을 바꾸는 흐름은 그 계정 줄을 FOR UPDATE로 먼저 잠그고, 주최자 줄을
 * 고치는 흐름은 주최자 줄에서 기다린다. 그래서 다시 확인한 권한은 트랜잭션이 끝날 때까지 그대로이고, 조작 기록에는 이 권한(그때의 역할)을 남긴다.
 *
 * @param permission 잠근 뒤 다시 확인한 권한
 * @param actor      조작하는 사람의 운영진 계정 — 외래 키 확인 잠금을 걸고 읽었다
 * @param organizer  FOR UPDATE로 잠그고 읽은 주최자
 */
record LockedOrganizer(StaffPermissionResult permission, StaffAccount actor, Organizer organizer) {

    LockedOrganizer {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(organizer, "organizer");
    }

    /**
     * 권한 확인 → 계정 줄 → 주최자 줄 → 권한 다시 확인.
     *
     * @throws com.ticketon.domain.error.PermissionDeniedException 처음 확인이나 잠근 뒤의 다시 확인에서 막히면 — 둘 다
     *                                                             {@link CheckStaffPermissionUseCase#check}의 규칙이다
     */
    static LockedOrganizer lock(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            StaffAccountRepository staffAccountRepository, OrganizerRepository organizerRepository,
            CheckStaffPermissionQuery query) {
        StaffPermissionResult checked = checkStaffPermissionUseCase.check(query);
        long staffAccountId = checked.staffAccountId();
        long organizerId = checked.organizerId();
        StaffAccount actor = StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, staffAccountId)
                .get(staffAccountId);
        if (actor == null) {
            throw new IllegalStateException("권한을 확인한 운영진 계정이 없습니다: " + staffAccountId);
        }
        Organizer organizer = organizerRepository.findByIdForUpdate(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        StaffPermissionResult recheckedAfterLock = checkStaffPermissionUseCase.check(query);
        return new LockedOrganizer(recheckedAfterLock, actor, organizer);
    }
}
