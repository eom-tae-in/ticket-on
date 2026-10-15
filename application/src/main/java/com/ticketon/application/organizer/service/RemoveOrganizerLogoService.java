package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.RemoveOrganizerLogoUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 로고 지우기 — 지우는 규칙(활동 중인 주최자만, 지우면 첫 글자 + 정해진 색으로 돌아감)은 {@link Organizer#removeLogo}가 보고, 여기서는
 * 권한을 확인하고 주최자를 잠가 읽어 넘긴 뒤 저장하고 조작 기록을 남기고, 커밋되면 로고 파일을 지운다({@link LogoFileCleanup}).
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 지우는 사람의 계정 줄 잡기 → 주최자 잠가 읽기 → 권한 다시 확인 → 지우기 → 저장 → 조작 기록
 * 순서. 로고가 없으면 저장도 기록도 하지 않는다.
 * <p>
 * <b>잠금 순서는 계정 줄 → 주최자 줄이다</b>({@link StaffAccountLocks}). 조작 기록이 가리킬 지우는 사람의 계정 줄에 외래 키 확인 잠금을 주최자
 * 줄보다 먼저 건다 — 그 계정 줄을 먼저 잠그고 소속 · 이력 · 조작 기록을 넣으며 주최자 줄에 외래 키 확인 잠금을 거는 흐름(소유자 넘기기, 내보내기,
 * 나가기, 소유자 이전 신청과 그 승인)과 거꾸로 맞물려 교착하지 않게(#195). 엇갈리면 늦은 쪽이 계정 줄에서 차례를 기다린다. 기다린 뒤에는 명단을 다시 읽어 권한을 다시 확인한다
 * ({@link LockedOrganizer}) — 그 사이 내보내졌거나 나갔으면 403 NOT_ORGANIZER_STAFF로 막아, 소속이 끝난 뒤의 조작이 처리 · 기록되지 않는다.
 */
@Service
public class RemoveOrganizerLogoService implements RemoveOrganizerLogoUseCase {

    static final String OPERATION_SUMMARY = "로고 삭제";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerLogoStoragePort logoStorage;
    private final Clock clock;

    public RemoveOrganizerLogoService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            StaffAccountRepository staffAccountRepository, OrganizerLogoStoragePort logoStorage, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.logoStorage = logoStorage;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrganizerPublicProfileResult remove(RemoveOrganizerLogoCommand command) {
        Instant now = clock.instant();
        LockedOrganizer locked = LockedOrganizer.lock(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_PUBLIC_PROFILE));
        StaffPermissionResult permission = locked.permission();
        Organizer organizer = locked.organizer();
        long organizerId = permission.organizerId();

        Optional<OrganizerLogo> removedLogo = organizer.removeLogo();
        if (removedLogo.isPresent()) {
            organizerRepository.savePublicProfile(organizer);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                    StaffOperationTargetKind.ORGANIZER, organizerId, OPERATION_SUMMARY, now));
            LogoFileCleanup.deleteAfterCommit(logoStorage, removedLogo.get());
        }
        return OrganizerPublicProfileResults.of(organizer, logoStorage);
    }
}
