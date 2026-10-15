package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.LATE_SUMMER_DETAILS;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.TWELVE_MONTHS_DETAILS;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeResult;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.domain.organizer.TelephoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(비사업자)의 대표 박정훈(소유자)이 정산 계좌를 국민은행에서 우리은행으로 바꾸려 하고, 늦여름(사업자)의 대표 최여름이 사업장 주소를
 * 옮겨 사업자 정보를 바꾸려 한다({@link OrganizerStaffScene}). 요청만 남고, 관리자가 확인하기 전까지 지금 값은 그대로다
 * (docs/01 "가로채기를 막는 장치", docs/11 "정산 계좌 변경").
 */
@DisplayName("정산 계좌 · 사업자 정보 변경 요청 유스케이스")
class RequestOrganizerChangeServiceTest {

    private static final SettlementAccount WOORI = new SettlementAccount("우리은행", "1002-345-678901", "박정훈");

    private static final BusinessInformation MOVED_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"), BusinessRegistrationNumber.of("123-45-67890"));

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final RequestOrganizerChangeService service = scene.requestOrganizerChangeService();

    private RequestOrganizerChangeResult requestAccount(long staffAccountId, SettlementAccount account) {
        return service.request(new RequestOrganizerChangeCommand(acting(staffAccountId, scene.twelveMonths),
                new RequestedOrganizerChange.NewSettlementAccount(account)));
    }

    private RequestOrganizerChangeResult requestBusiness(long staffAccountId, long organizerId) {
        return service.request(new RequestOrganizerChangeCommand(acting(staffAccountId, organizerId),
                new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS)));
    }

    private void assertNothingRequested() {
        assertThat(scene.changeRequestRepository.savedRequests()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.organizerRepository.findBusinessDetails(scene.twelveMonths)).contains(TWELVE_MONTHS_DETAILS);
    }

    @Test
    @DisplayName("소유자가 새 정산 계좌를 요청하면 확인 중인 요청이 남고, 지금 정산 계좌는 그대로다 — 확인 중에는 기존 계좌가 쓰인다")
    void ownerRequestsSettlementAccountChange() {
        RequestOrganizerChangeResult result = requestAccount(JUNGHOON, WOORI);

        assertThat(scene.changeRequestRepository.savedRequests()).singleElement().satisfies(request -> {
            assertThat(request.id()).hasValue(result.changeRequestId());
            assertThat(request.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(request.requesterStaffAccountId()).isEqualTo(JUNGHOON);
            assertThat(request.requestedChange()).isEqualTo(new RequestedOrganizerChange.NewSettlementAccount(WOORI));
            assertThat(request.requestedAt()).isEqualTo(NOW);
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
        });
        assertThat(result.kind()).isEqualTo(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT);
        assertThat(result.requestedAt()).isEqualTo(NOW);
        assertThat(scene.organizerRepository.findBusinessDetails(scene.twelveMonths)).contains(TWELVE_MONTHS_DETAILS);
    }

    @Test
    @DisplayName("소유자의 조작 기록이 주최자를 대상으로 '정산 계좌 변경 요청'으로 남는다 — 계좌번호는 싣지 않는다. 메일은 없다")
    void recordsOperationWithoutValues() {
        requestAccount(JUNGHOON, WOORI);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(JUNGHOON);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OWNER);
            assertThat(record.action()).isEqualTo(StaffAction.MANAGE_SETTLEMENT_ACCOUNT);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
            assertThat(record.targetId()).isEqualTo(scene.twelveMonths);
            assertThat(record.summary()).isEqualTo("정산 계좌 변경 요청").doesNotContain("1002", "678901");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("사업자 주최자의 소유자는 새 사업자 정보를 요청한다 — 조작 기록은 '사업자 정보 변경 요청'이다")
    void businessOwnerRequestsBusinessInformationChange() {
        RequestOrganizerChangeResult result = requestBusiness(YEOREUM, scene.lateSummer);

        assertThat(result.kind()).isEqualTo(OrganizerChangeRequestKind.BUSINESS_INFORMATION);
        assertThat(scene.changeRequestRepository.savedRequests()).singleElement()
                .extracting(OrganizerChangeRequest::requestedChange)
                .isEqualTo(new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS));
        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo(StaffAction.CHANGE_BUSINESS_INFORMATION);
            assertThat(record.summary()).isEqualTo("사업자 정보 변경 요청");
        });
        assertThat(scene.organizerRepository.findBusinessDetails(scene.lateSummer)).contains(LATE_SUMMER_DETAILS);
    }

    @Test
    @DisplayName("정산 계좌 요청과 사업자 정보 요청은 함께 확인 중일 수 있다")
    void bothKindsCanBeUnderReview() {
        service.request(new RequestOrganizerChangeCommand(acting(YEOREUM, scene.lateSummer),
                new RequestedOrganizerChange.NewSettlementAccount(WOORI)));
        requestBusiness(YEOREUM, scene.lateSummer);

        assertThat(scene.changeRequestRepository.savedRequests()).extracting(OrganizerChangeRequest::kind)
                .containsExactly(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT, OrganizerChangeRequestKind.BUSINESS_INFORMATION);
    }

    @Test
    @DisplayName("운영자는 요청하지 못한다 (OWNER_ONLY_ACTION) — 정산 계좌도 사업자 정보도. 아무 줄도 잠그지 않고 아무것도 남지 않는다")
    void operatorCannotRequest() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> requestAccount(PLANNER, WOORI))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.OWNER_ONLY_ACTION));
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> requestBusiness(DOHYUN, scene.lateSummer))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.OWNER_ONLY_ACTION));

        assertNothingRequested();
        assertThat(scene.rowLockLog.locks()).isEmpty();
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 요청하지 못한다 — 다른 주최자의 소유자도 (NOT_ORGANIZER_STAFF)")
    void outsiderCannotRequest() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> requestAccount(YEOREUM, WOORI))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));

        assertNothingRequested();
    }

    @Test
    @DisplayName("비사업자 주최자는 사업자 정보 변경을 요청하지 못한다 (NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION)")
    void nonBusinessCannotRequestBusinessInformation() {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> requestBusiness(JUNGHOON, scene.twelveMonths))
                .satisfies(rejected -> assertThat(rejected.errorCode())
                        .isEqualTo(OrganizerErrorCode.NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION));

        assertNothingRequested();
    }

    @Test
    @DisplayName("같은 종류의 요청이 확인 중이면 결과를 기다린다 (ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW)")
    void waitsForRequestUnderReview() {
        requestAccount(JUNGHOON, WOORI);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> requestAccount(JUNGHOON, new SettlementAccount("하나은행", "111-222222-33333", "박정훈")))
                .satisfies(rejected -> assertThat(rejected.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW));
        assertThat(scene.changeRequestRepository.savedRequests()).hasSize(1);
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("지금 정산 계좌와 같으면(앞뒤 공백만 다름) 요청하지 않는다 (ORGANIZER_CHANGE_SAME_AS_CURRENT)")
    void sameAsCurrentIsRejected() {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> requestAccount(JUNGHOON, new SettlementAccount(" 국민은행", "123456-78-901234 ", "박정훈")))
                .satisfies(rejected -> assertThat(rejected.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_SAME_AS_CURRENT));

        assertNothingRequested();
    }

    @Test
    @DisplayName("잠금 순서 — 요청하는 사람의 계정 줄에 외래 키 확인 잠금을 주최자 줄보다 먼저 건다 (계정 줄 → 주최자 줄, #195)")
    void locksRequesterAccountBeforeOrganizer() {
        requestAccount(JUNGHOON, WOORI);

        assertThat(scene.rowLockLog.locks()).containsExactly("계정 " + JUNGHOON + " FOR KEY SHARE",
                "주최자 " + scene.twelveMonths + " FOR UPDATE");
        assertThat(scene.staffAccountRepository.lockedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("계정 줄을 기다리는 사이 소유자를 넘겼으면 잠근 뒤 다시 확인해 막는다 (OWNER_ONLY_ACTION) — 넘긴 사람의 요청이 남지 않는다")
    void ownerTransferredWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(() -> scene.staffRepository.saveOwnershipTransfer(
                scene.staffRepository.findByOrganizerId(scene.twelveMonths).transferOwnership(JUNGHOON, PLANNER, NOW)));

        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> requestAccount(JUNGHOON, WOORI))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.OWNER_ONLY_ACTION));

        assertNothingRequested();
    }

    @Test
    @DisplayName("확인한 뒤 거의 동시에 낸 같은 종류의 요청이 먼저 저장됐으면 409이고 조작 기록이 남지 않는다")
    void concurrentRequestConflicts() {
        scene.changeRequestRepository.addBeforeNextAdd(OrganizerChangeRequest.request(scene.twelveMonths, JUNGHOON,
                TWELVE_MONTHS_DETAILS, new RequestedOrganizerChange.NewSettlementAccount(
                        new SettlementAccount("하나은행", "111-222222-33333", "박정훈")), false, NOW));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> requestAccount(JUNGHOON, WOORI))
                .satisfies(conflict -> assertThat(conflict.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW));
        assertThat(scene.changeRequestRepository.savedRequests()).hasSize(1);
        assertThat(scene.recordRepository.records()).isEmpty();
    }
}
