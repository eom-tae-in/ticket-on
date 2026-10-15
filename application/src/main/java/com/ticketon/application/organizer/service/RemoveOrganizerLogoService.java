package com.ticketon.application.organizer.service;

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
 * <b>한 트랜잭션이다.</b> 권한 확인 → 주최자 잠가 읽기 → 지우기 → 저장 → 조작 기록 순서. 로고가 없으면 저장도 기록도 하지 않는다.
 */
@Service
public class RemoveOrganizerLogoService implements RemoveOrganizerLogoUseCase {

    static final String OPERATION_SUMMARY = "로고 삭제";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerLogoStoragePort logoStorage;
    private final Clock clock;

    public RemoveOrganizerLogoService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            OrganizerLogoStoragePort logoStorage, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.logoStorage = logoStorage;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrganizerPublicProfileResult remove(RemoveOrganizerLogoCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_PUBLIC_PROFILE));
        long organizerId = permission.organizerId();
        Organizer organizer = organizerRepository.findByIdForUpdate(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));

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
