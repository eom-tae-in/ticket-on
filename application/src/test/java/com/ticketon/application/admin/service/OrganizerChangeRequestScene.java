package com.ticketon.application.admin.service;

import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 · 승인 · 반려 유스케이스 테스트가 함께 쓰는 장면 — 늦여름(docs/15, 사업자)의 대표 최여름이 정산 계좌를
 * 신한은행에서 우리은행으로 바꾸고, 사업장 주소를 옮겨 사업자 정보도 바꿔 달라고 요청했다(docs/11 "정산 계좌 변경", docs/01 "판매자 정보").
 * <ul>
 *   <li>늦여름 — 최여름(늦은여름)이 소유자, 이도현(밤의기타)이 운영자. 활동 중. 사업자, 신한은행 계좌.</li>
 *   <li>최여름이 2026-10-15 10:00에 낸 정산 계좌 변경 요청과 10:30에 낸 사업자 정보 변경 요청 — 둘 다 확인 중.</li>
 * </ul>
 * 시계는 다음 날 2026-10-16 15:00(한국 시각)에 멈춰 있다. 줄 잠금은 한 기록에 차례대로 남는다.
 */
final class OrganizerChangeRequestScene {

    static final long YEOREUM = 3L;
    static final long DOHYUN = 4L;

    static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2023, 3, 4, 10, 0));
    static final Instant REQUESTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));
    static final Instant NOW = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 15, 0));

    static final BusinessInformation LATE_SUMMER_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 와우산로 12", TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"));

    static final SettlementAccount SHINHAN = new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드");

    static final OrganizerBusinessDetails LATE_SUMMER_DETAILS =
            new OrganizerBusinessDetails(BusinessDeclaration.business(LATE_SUMMER_BUSINESS), SHINHAN);

    static final SettlementAccount WOORI = new SettlementAccount("우리은행", "1002-345-678901", "최여름");

    static final BusinessInformation MOVED_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"), BusinessRegistrationNumber.of("123-45-67890"));

    final MutableClock clock = MutableClock.at(NOW);
    final RowLockLog rowLockLog = new RowLockLog();
    final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository(rowLockLog);
    final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository(rowLockLog);
    final FakeOrganizerChangeRequestRepository changeRequestRepository = new FakeOrganizerChangeRequestRepository();
    final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();

    final long lateSummer;
    final long accountRequest;
    final long businessRequest;

    OrganizerChangeRequestScene() {
        staffAccountRepository.storeActive(YEOREUM, "최여름", "늦은여름", "yeoreum@example.com");
        staffAccountRepository.storeActive(DOHYUN, "이도현", "밤의기타", "dohyun@example.com");

        lateSummer = organizerRepository.store("늦여름", OrganizerStatus.ACTIVE);
        organizerRepository.storeBusinessDetails(lateSummer, LATE_SUMMER_DETAILS);
        OrganizerStaff staff = OrganizerStaff.startWithOwner(lateSummer, YEOREUM, APPROVED_AT);
        staff.addOperator(DOHYUN, APPROVED_AT);
        staffRepository.store(staff);

        accountRequest = changeRequestRepository.store(lateSummer, YEOREUM,
                new RequestedOrganizerChange.NewSettlementAccount(WOORI), REQUESTED_AT);
        businessRequest = changeRequestRepository.store(lateSummer, YEOREUM,
                new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS), REQUESTED_AT.plusSeconds(1_800));
    }

    AccountRef storeAdmin(AdminGrade grade) {
        long id = adminAccountRepository.store(grade.name().toLowerCase() + "@ticket-on.example",
                FakePasswordHashPort.hashOf("admin-1234"), grade, APPROVED_AT);
        return AccountRef.of(AccountKind.ADMIN, id);
    }

    /** 최여름이 이도현에게 소유자를 넘긴 것으로 명단을 바꾼다 — 요청한 뒤 소유자가 바뀐 경우. */
    void transferOwnershipToDohyun() {
        staffRepository.saveOwnershipTransfer(
                staffRepository.findByOrganizerId(lateSummer).transferOwnership(YEOREUM, DOHYUN, REQUESTED_AT.plusSeconds(3_600)));
    }

    ApproveOrganizerChangeRequestService approveService() {
        return new ApproveOrganizerChangeRequestService(adminAccountRepository, changeRequestRepository,
                organizerRepository, staffRepository, staffAccountRepository, new AdminOperationRecorder(recordRepository),
                sendNotificationUseCase, clock);
    }

    RejectOrganizerChangeRequestService rejectService() {
        return new RejectOrganizerChangeRequestService(adminAccountRepository, changeRequestRepository,
                organizerRepository, new AdminOperationRecorder(recordRepository), sendNotificationUseCase, clock);
    }

    GetOrganizerChangeRequestReviewService reviewService() {
        return new GetOrganizerChangeRequestReviewService(adminAccountRepository, changeRequestRepository,
                organizerRepository, staffRepository, staffAccountRepository);
    }
}
