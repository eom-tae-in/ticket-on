package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileCommand;
import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 공개 정보(소개 · SNS 링크) 편집 — 바꾸는 규칙(활동 중인 주최자만, 비우면 없는 것)은 {@link Organizer#changeIntroductionAndSnsLinks}가
 * 보고, 여기서는 권한을 확인하고 주최자를 잠가 읽어 넘긴 뒤 바뀌었으면 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 주최자 잠가 읽기 → 바꾸기 → 저장 → 조작 기록 순서. 지금과 같은 값이면 저장도 기록도 하지 않는다.
 */
@Service
public class ChangeOrganizerPublicProfileService implements ChangeOrganizerPublicProfileUseCase {

    static final String OPERATION_SUMMARY = "공개 정보 수정 (소개 · SNS 링크)";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerLogoStoragePort logoStorage;
    private final Clock clock;

    public ChangeOrganizerPublicProfileService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
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
    public OrganizerPublicProfileResult change(ChangeOrganizerPublicProfileCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_PUBLIC_PROFILE));
        long organizerId = permission.organizerId();
        Organizer organizer = organizerRepository.findByIdForUpdate(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));

        if (organizer.changeIntroductionAndSnsLinks(command.introduction(), command.snsLinks())) {
            organizerRepository.savePublicProfile(organizer);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                    StaffOperationTargetKind.ORGANIZER, organizerId, OPERATION_SUMMARY, now));
        }
        return OrganizerPublicProfileResults.of(organizer, logoStorage);
    }
}
