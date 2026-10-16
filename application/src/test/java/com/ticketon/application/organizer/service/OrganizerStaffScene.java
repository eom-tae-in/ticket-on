package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.TokenGenerator;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 운영자 초대 · 수락 · 거절 · 내보내기 · 나가기 · 소유자 넘기기 · 소유자 이전 신청 · 공개 정보 편집 · 주최자명 변경 · 정산 계좌 · 사업자
 * 정보 변경 요청 유스케이스 테스트가 함께 쓰는 장면 — docs/15의 두 주최자와 세 사람.
 * <ul>
 *   <li>극단 열두달 — 대표 박정훈이 소유자, 기획 단원이 운영자. 활동 중. 비사업자, 국민은행 계좌.</li>
 *   <li>늦여름 — 최여름이 소유자, 이도현이 운영자. 활동 중. 사업자, 신한은행 계좌. 이도현은 열두달에는 바깥 사람이다.</li>
 *   <li>닫힌 주최자 극단 봄날 — 운영진이 남지 않았다.</li>
 * </ul>
 * 시계는 2026-10-15 10:00(한국 시각)에 멈춰 있다. 권한 확인과 조작 기록은 진짜 유스케이스를 메모리 저장소로 돌린다.
 */
final class OrganizerStaffScene {

    static final long JUNGHOON = 1L;
    static final long PLANNER = 2L;
    static final long YEOREUM = 3L;
    static final long DOHYUN = 4L;

    static final String JUNGHOON_EMAIL = "junghoon@example.com";
    static final String PLANNER_EMAIL = "planner@example.com";
    static final String DOHYUN_EMAIL = "dohyun@example.com";

    static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));
    static final Instant NOW = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    static final WebAddress WEB_ADDRESS = WebAddress.of("https://ticket-on.example");

    /** 극단 열두달의 지금 사업자 정보 · 정산 계좌 — 비사업자, 국민은행 계좌. */
    static final OrganizerBusinessDetails TWELVE_MONTHS_DETAILS = new OrganizerBusinessDetails(
            BusinessDeclaration.nonBusiness(), new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));

    /** 늦여름의 지금 사업자 정보 · 정산 계좌 — 사업자, 신한은행 계좌. */
    static final OrganizerBusinessDetails LATE_SUMMER_DETAILS = new OrganizerBusinessDetails(
            BusinessDeclaration.business(new BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                    TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"))),
            new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));

    final MutableClock clock = MutableClock.at(NOW);
    final RowLockLog rowLockLog = new RowLockLog();
    final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository(rowLockLog);
    final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    final FakeStaffInvitationRepository invitationRepository = new FakeStaffInvitationRepository();
    final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository(rowLockLog);
    final FakeOwnershipChangeRepository ownershipChangeRepository = new FakeOwnershipChangeRepository();
    final FakeOwnershipClaimRepository ownershipClaimRepository = new FakeOwnershipClaimRepository();
    final FakeOrganizerNameChangeRepository nameChangeRepository = new FakeOrganizerNameChangeRepository();
    final FakeOrganizerChangeRequestRepository changeRequestRepository = new FakeOrganizerChangeRequestRepository();
    final RecordingStaffOperationRecordRepository recordRepository = new RecordingStaffOperationRecordRepository();
    final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    final FakeOrganizerLogoStorage logoStorage = new FakeOrganizerLogoStorage();
    final CheckStaffPermissionService checkStaffPermissionService =
            new CheckStaffPermissionService(organizerRepository, staffRepository);
    final RecordStaffOperationService recordStaffOperationService = new RecordStaffOperationService(recordRepository);

    final long twelveMonths;
    final long lateSummer;
    final long closedSpring;

    OrganizerStaffScene() {
        staffAccountRepository.storeActive(JUNGHOON, "박정훈", "조용한고래", JUNGHOON_EMAIL);
        staffAccountRepository.storeActive(PLANNER, "김기획", "푸른바람", PLANNER_EMAIL);
        staffAccountRepository.storeActive(YEOREUM, "최여름", "늦은여름", "yeoreum@example.com");
        staffAccountRepository.storeActive(DOHYUN, "이도현", "밤의기타", DOHYUN_EMAIL);

        twelveMonths = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE).id().orElseThrow();
        OrganizerStaff twelveMonthsStaff = OrganizerStaff.startWithOwner(twelveMonths, JUNGHOON, APPROVED_AT);
        twelveMonthsStaff.addOperator(PLANNER, APPROVED_AT.plusSeconds(86_400));
        staffRepository.store(twelveMonthsStaff);

        organizerRepository.storeBusinessDetails(twelveMonths, TWELVE_MONTHS_DETAILS);

        lateSummer = organizerRepository.store("늦여름", OrganizerStatus.ACTIVE).id().orElseThrow();
        organizerRepository.storeBusinessDetails(lateSummer, LATE_SUMMER_DETAILS);
        OrganizerStaff lateSummerStaff = OrganizerStaff.startWithOwner(lateSummer, YEOREUM, APPROVED_AT);
        lateSummerStaff.addOperator(DOHYUN, APPROVED_AT);
        staffRepository.store(lateSummerStaff);

        closedSpring = organizerRepository.store("극단 봄날", OrganizerStatus.CLOSED).id().orElseThrow();
    }

    InviteOperatorService inviteOperatorService() {
        return new InviteOperatorService(checkStaffPermissionService, recordStaffOperationService, organizerRepository,
                staffRepository, invitationRepository, staffAccountRepository, sendNotificationUseCase, WEB_ADDRESS, clock);
    }

    AcceptStaffInvitationService acceptStaffInvitationService() {
        return new AcceptStaffInvitationService(staffAccountRepository, invitationRepository, organizerRepository,
                staffRepository, checkStaffPermissionService, recordStaffOperationService, clock);
    }

    DeclineStaffInvitationService declineStaffInvitationService() {
        return new DeclineStaffInvitationService(staffAccountRepository, invitationRepository, clock);
    }

    RemoveOperatorService removeOperatorService() {
        return new RemoveOperatorService(checkStaffPermissionService, recordStaffOperationService, organizerRepository,
                staffRepository, staffAccountRepository, sendNotificationUseCase, clock);
    }

    LeaveOrganizerService leaveOrganizerService() {
        return new LeaveOrganizerService(checkStaffPermissionService, recordStaffOperationService, staffRepository,
                staffAccountRepository, clock);
    }

    TransferOwnershipService transferOwnershipService() {
        return new TransferOwnershipService(checkStaffPermissionService, recordStaffOperationService, organizerRepository,
                staffRepository, ownershipChangeRepository, staffAccountRepository, sendNotificationUseCase, clock);
    }

    SubmitOwnershipClaimService submitOwnershipClaimService() {
        return new SubmitOwnershipClaimService(checkStaffPermissionService, recordStaffOperationService, staffRepository,
                ownershipClaimRepository, staffAccountRepository, clock);
    }

    GetOrganizerPublicProfileService getOrganizerPublicProfileService() {
        return new GetOrganizerPublicProfileService(checkStaffPermissionService, organizerRepository, logoStorage);
    }

    ChangeOrganizerPublicProfileService changeOrganizerPublicProfileService() {
        return new ChangeOrganizerPublicProfileService(checkStaffPermissionService, recordStaffOperationService,
                organizerRepository, staffAccountRepository, logoStorage, clock);
    }

    UploadOrganizerLogoService uploadOrganizerLogoService(TokenGenerator tokenGenerator) {
        return new UploadOrganizerLogoService(checkStaffPermissionService, recordStaffOperationService,
                organizerRepository, staffAccountRepository, logoStorage, tokenGenerator, clock);
    }

    RemoveOrganizerLogoService removeOrganizerLogoService() {
        return new RemoveOrganizerLogoService(checkStaffPermissionService, recordStaffOperationService,
                organizerRepository, staffAccountRepository, logoStorage, clock);
    }

    RenameOrganizerService renameOrganizerService() {
        return new RenameOrganizerService(checkStaffPermissionService, recordStaffOperationService, organizerRepository,
                nameChangeRepository, staffAccountRepository, sendNotificationUseCase, clock);
    }

    RequestOrganizerChangeService requestOrganizerChangeService() {
        return new RequestOrganizerChangeService(checkStaffPermissionService, recordStaffOperationService,
                organizerRepository, changeRequestRepository, staffAccountRepository, clock);
    }

    GetOrganizerBusinessDetailsService getOrganizerBusinessDetailsService() {
        return new GetOrganizerBusinessDetailsService(checkStaffPermissionService, organizerRepository,
                changeRequestRepository);
    }

    static ActingStaff acting(long staffAccountId, long organizerId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), organizerId);
    }

    static AnswerStaffInvitationCommand answer(long staffAccountId, long invitationId) {
        return new AnswerStaffInvitationCommand(AccountRef.of(AccountKind.STAFF, staffAccountId), invitationId);
    }
}
