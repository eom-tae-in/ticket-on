package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoUseCase;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.common.TokenGenerator;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자 로고 올리기 — 그림이 로고로 받을 수 있는지(정사각형 · 용량 상한 · 받는 형식)는 {@link OrganizerLogoImage}가, 로고를 바꾸는 규칙(활동
 * 중인 주최자만)은 {@link Organizer#changeLogo}가 본다. 여기서는 권한을 확인하고, 추측할 수 없는 열쇠를 붙여 파일을 저장하고, 주최자를 저장하고,
 * 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 올리는 사람의 계정 줄 잡기 → 주최자 잠가 읽기 → 열쇠 붙여 로고 바꾸기 → 파일 저장 → 주최자 저장 → 조작
 * 기록 순서. 파일 저장소는 트랜잭션에 묶이지 않아, 되돌려지면 새 파일을 지우고 커밋되면 바꾸기 전 로고의 파일을 지운다({@link LogoFileCleanup}).
 * 주최자를 잠가 읽으므로 두 운영진이 거의 동시에 올려도 뒤의 요청은 앞의 로고를 "바꾸기 전 로고"로 읽어 그 파일을 지운다.
 * <p>
 * <b>잠금 순서는 계정 줄 → 주최자 줄이다</b>({@link StaffAccountLocks}). 조작 기록이 가리킬 올리는 사람의 계정 줄에 외래 키 확인 잠금을 주최자
 * 줄보다 먼저 건다 — 그 계정 줄을 먼저 잠그고 소속 · 이력 · 조작 기록을 넣으며 주최자 줄에 외래 키 확인 잠금을 거는 흐름(소유자 넘기기, 내보내기,
 * 나가기, 소유자 이전 신청과 그 승인)과 거꾸로 맞물려 교착하지 않게(#195). 엇갈리면 늦은 쪽이 계정 줄에서 차례를 기다린다.
 */
@Service
public class UploadOrganizerLogoService implements UploadOrganizerLogoUseCase {

    static final String OPERATION_SUMMARY = "로고 올리기";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerLogoStoragePort logoStorage;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public UploadOrganizerLogoService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            StaffAccountRepository staffAccountRepository, OrganizerLogoStoragePort logoStorage,
            TokenGenerator tokenGenerator, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.logoStorage = logoStorage;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OrganizerPublicProfileResult upload(UploadOrganizerLogoCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_PUBLIC_PROFILE));
        long organizerId = permission.organizerId();
        StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, permission.staffAccountId());
        Organizer organizer = organizerRepository.findByIdForUpdate(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        OrganizerLogoImage image = command.image();
        OrganizerLogo newLogo = OrganizerLogo.issue(tokenGenerator.urlSafe(OrganizerLogo.TOKEN_LENGTH), image.format());

        Optional<OrganizerLogo> previousLogo = organizer.changeLogo(newLogo);
        logoStorage.store(newLogo, image);
        LogoFileCleanup.deleteOnRollback(logoStorage, newLogo);
        organizerRepository.savePublicProfile(organizer);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.ORGANIZER,
                organizerId, OPERATION_SUMMARY, now));
        previousLogo.ifPresent(logo -> LogoFileCleanup.deleteAfterCommit(logoStorage, logo));
        return OrganizerPublicProfileResults.of(organizer, logoStorage);
    }
}
