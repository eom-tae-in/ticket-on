package com.ticketon.application.admin.service;

import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 소유자 이전 신청 심사 · 승인 · 반려 유스케이스 테스트가 함께 쓰는 장면 — 극단 열두달(docs/15 박정훈)의 전 대표가 졸업하고 연락이 끊겨, 운영자인
 * 기획 단원이 관리자에게 소유자 이전을 신청했다(docs/01 "소유자와 연락이 끊긴 경우").
 * <ul>
 *   <li>극단 열두달 — 대표 박정훈(조용한고래)이 소유자, 기획 단원(푸른바람) · 홍보 단원(구름)이 운영자. 활동 중. 2019년에 신청서가 승인됐다.</li>
 *   <li>기획 단원이 2026-10-15 10:00에 낸 심사 중 신청 하나 — 신청 때의 소유자는 박정훈.</li>
 *   <li>이도현(밤의기타)은 다른 주최자의 운영자라 열두달에는 바깥 사람이다.</li>
 * </ul>
 * 시계는 다음 날 2026-10-16 15:00(한국 시각)에 멈춰 있다. 관리자 이도현(관리자 계정)이 심사한다.
 */
final class OwnershipClaimScene {

    static final long JUNGHOON = 1L;
    static final long PLANNER = 2L;
    static final long PROMOTER = 3L;
    static final long DOHYUN = 4L;

    static final String CLAIM_REASON = "전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다. 올해 정기공연을 준비해야 합니다.";

    static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));
    static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));
    static final Instant NOW = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 15, 0));

    final MutableClock clock = MutableClock.at(NOW);
    final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();
    final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository();
    final FakeOwnershipClaimRepository claimRepository = new FakeOwnershipClaimRepository();
    final FakeOwnershipChangeRepository ownershipChangeRepository = new FakeOwnershipChangeRepository();
    final FakeOrganizerApplicationRepository applicationRepository = new FakeOrganizerApplicationRepository();
    final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    final FakeOrganizerStaffContactPort staffContactPort =
            new FakeOrganizerStaffContactPort(staffRepository, staffAccountRepository);

    final long twelveMonths;
    final long plannerClaim;
    final long approvedApplication;

    OwnershipClaimScene() {
        staffAccountRepository.storeActive(JUNGHOON, "박정훈", "조용한고래", "junghoon@example.com");
        staffAccountRepository.storeActive(PLANNER, "김기획", "푸른바람", "planner@example.com");
        staffAccountRepository.storeActive(PROMOTER, "정홍보", "구름", "promoter@example.com");
        staffAccountRepository.storeActive(DOHYUN, "이도현", "밤의기타", "dohyun@example.com");

        twelveMonths = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE);
        OrganizerStaff staff = OrganizerStaff.startWithOwner(twelveMonths, JUNGHOON, APPROVED_AT);
        staff.addOperator(PLANNER, APPROVED_AT);
        staff.addOperator(PROMOTER, APPROVED_AT);
        staffRepository.store(staff);
        approvedApplication = applicationRepository.storeApproved(twelveMonths, JUNGHOON,
                OrganizerApplicationForms.twelveMonths(), APPROVED_AT.minusSeconds(259_200), APPROVED_AT);

        plannerClaim = claimRepository.store(twelveMonths, PLANNER, JUNGHOON, CLAIM_REASON, SUBMITTED_AT);
    }

    AccountRef storeAdmin(AdminGrade grade) {
        long id = adminAccountRepository.store(grade.name().toLowerCase() + "@ticket-on.example",
                FakePasswordHashPort.hashOf("admin-1234"), grade, APPROVED_AT);
        return AccountRef.of(AccountKind.ADMIN, id);
    }

    ApproveOwnershipClaimService approveService() {
        return new ApproveOwnershipClaimService(adminAccountRepository, claimRepository, organizerRepository,
                staffRepository, staffAccountRepository, ownershipChangeRepository,
                new AdminOperationRecorder(recordRepository), sendNotificationUseCase, clock);
    }

    RejectOwnershipClaimService rejectService() {
        return new RejectOwnershipClaimService(adminAccountRepository, claimRepository, organizerRepository,
                staffAccountRepository, staffContactPort, new AdminOperationRecorder(recordRepository),
                sendNotificationUseCase, clock);
    }

    GetOwnershipClaimReviewService reviewService() {
        return new GetOwnershipClaimReviewService(adminAccountRepository, claimRepository, organizerRepository,
                staffRepository, staffAccountRepository, applicationRepository);
    }
}
