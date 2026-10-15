package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerPublicProfileUseCase;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.StaffAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 공개 정보 편집 화면 조회 — 편집할 수 있는 사람인지({@link StaffAction#EDIT_PUBLIC_PROFILE}, 운영진 누구나) 확인한 뒤 공개 정보를 읽어
 * 옮긴다. 보기만 하므로 조작 기록을 남기지 않는다.
 */
@Service
public class GetOrganizerPublicProfileService implements GetOrganizerPublicProfileUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerLogoStoragePort logoStorage;

    public GetOrganizerPublicProfileService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            OrganizerRepository organizerRepository, OrganizerLogoStoragePort logoStorage) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.organizerRepository = organizerRepository;
        this.logoStorage = logoStorage;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerPublicProfileResult get(GetOrganizerPublicProfileQuery query) {
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(query.actingStaff(), StaffAction.EDIT_PUBLIC_PROFILE));
        Organizer organizer = organizerRepository.findById(permission.organizerId())
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + permission.organizerId()));
        return OrganizerPublicProfileResults.of(organizer, logoStorage);
    }
}
