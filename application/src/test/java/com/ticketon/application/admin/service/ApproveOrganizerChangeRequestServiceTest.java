package com.ticketon.application.admin.service;

import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.LATE_SUMMER_DETAILS;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.MOVED_BUSINESS;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.NOW;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.WOORI;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.YEOREUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 관리자가 늦여름 대표 최여름의 정산 계좌 · 사업자 정보 변경 요청을 확인한 뒤 승인한다({@link OrganizerChangeRequestScene}). */
@DisplayName("정산 계좌 · 사업자 정보 변경 승인 유스케이스 (docs/11 \"정산 계좌 변경\", docs/01 \"판매자 정보\")")
class ApproveOrganizerChangeRequestServiceTest {

    private final OrganizerChangeRequestScene scene = new OrganizerChangeRequestScene();
    private final ApproveOrganizerChangeRequestService service = scene.approveService();
    private final AccountRef admin = scene.storeAdmin(AdminGrade.ADMIN);

    private void approve(long requestId) {
        service.approve(new ApproveOrganizerChangeRequestCommand(admin, requestId));
    }

    private OrganizerBusinessDetails storedDetails() {
        return scene.organizerRepository.findBusinessDetails(scene.lateSummer).orElseThrow();
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    /** 승인이 막혔으면 주최자 값 · 기록 · 메일 어느 것도 바뀌거나 남지 않는다. */
    private void assertNothingApproved() {
        assertThat(storedDetails()).isEqualTo(LATE_SUMMER_DETAILS);
        assertThat(scene.organizerRepository.businessDetailsSaves()).isZero();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AdminGrade.class)
    @DisplayName("승인하면 새 정산 계좌가 주최자에 적용되고 요청은 승인 · 처리 일시 · 처리한 관리자를 갖는다 — 관리자 · 최고 관리자 모두 한다")
    void approvalAppliesNewAccount(AdminGrade grade) {
        AccountRef reviewer = scene.storeAdmin(grade);

        service.approve(new ApproveOrganizerChangeRequestCommand(reviewer, scene.accountRequest));

        assertThat(storedDetails()).isEqualTo(LATE_SUMMER_DETAILS.withSettlementAccount(WOORI));
        assertThat(scene.changeRequestRepository.findById(scene.accountRequest).orElseThrow()).satisfies(request -> {
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.APPROVED);
            assertThat(request.reviewedAt()).contains(NOW);
            assertThat(request.reviewerAdminAccountId()).hasValue(reviewer.id());
        });
    }

    @Test
    @DisplayName("사업자 정보 변경을 승인하면 새 사업자 정보가 적용되고 정산 계좌는 그대로다 — 다른 종류의 확인 중 요청은 남는다")
    void approvalAppliesNewBusinessInformation() {
        approve(scene.businessRequest);

        assertThat(storedDetails()).isEqualTo(LATE_SUMMER_DETAILS.withBusinessInformation(MOVED_BUSINESS));
        assertThat(scene.changeRequestRepository.findById(scene.accountRequest).orElseThrow().status())
                .isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("두 종류를 차례로 승인하면 앞의 승인이 저장한 값에 뒤의 승인을 적용한다 — 서로의 값을 덮지 않는다")
    void approvalsDoNotOverwriteEachOther() {
        approve(scene.businessRequest);
        approve(scene.accountRequest);

        assertThat(storedDetails()).isEqualTo(LATE_SUMMER_DETAILS.withBusinessInformation(MOVED_BUSINESS)
                .withSettlementAccount(WOORI));
    }

    @Test
    @DisplayName("관리자 조작 기록이 한 줄 남는다 — 요약은 '정산 계좌 변경 승인 — 주최자명'이고 계좌번호는 싣지 않는다")
    void leavesOperationRecordWithoutValues() {
        approve(scene.accountRequest);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.operator()).isEqualTo(admin);
            assertThat(record.operatedAt()).isEqualTo(NOW);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.APPROVE_ORGANIZER_CHANGE_REQUEST);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_CHANGE_REQUEST);
            assertThat(record.targetId()).isEqualTo(scene.accountRequest);
            assertThat(record.summary()).isEqualTo("정산 계좌 변경 승인 — 늦여름").doesNotContain("1002", "678901");
            assertThat(record.reason()).isEmpty();
        });
    }

    @Test
    @DisplayName("소유자에게 '정산 계좌 변경 확인 결과' 메일([주최])을 보낸다 — 주최자 id로 보내 소유자에게만 간다. 계좌번호는 싣지 않는다")
    void notifiesOwnerOfSettlementAccountApproval() {
        approve(scene.accountRequest);

        assertThat(scene.sendNotificationUseCase.sentCommands()).singleElement().satisfies(sent -> {
            assertThat(sent.kind()).isEqualTo(NotificationKind.SETTLEMENT_ACCOUNT_CHANGE_DECIDED);
            assertThat(sent.recipient()).isEqualTo(NotificationRecipient.organizer(scene.lateSummer));
            assertThat(sent.content().title()).isEqualTo("'늦여름' 정산 계좌 변경이 승인되었습니다");
            assertThat(sent.content().body()).contains("이제부터 바꾼 정산 계좌로 정산됩니다.")
                    .doesNotContain("1002-345-678901", "우리은행");
        });
    }

    @Test
    @DisplayName("사업자 정보 변경 결과는 표 밖 종류 '사업자 정보 변경 확인 결과'로 소유자에게 간다")
    void notifiesOwnerOfBusinessInformationApproval() {
        approve(scene.businessRequest);

        assertThat(scene.sendNotificationUseCase.sentCommands()).singleElement().satisfies(sent -> {
            assertThat(sent.kind()).isEqualTo(NotificationKind.BUSINESS_INFORMATION_CHANGE_DECIDED);
            assertThat(sent.content().title()).isEqualTo("'늦여름' 사업자 정보 변경이 승인되었습니다");
            assertThat(sent.content().body()).doesNotContain("1234567890");
        });
    }

    @Test
    @DisplayName("잠금 순서 — 요청한 사람의 계정 줄(외래 키 확인 잠금) → 주최자 줄 순서로 잡는다 (계정 줄 → 주최자 줄 → 요청 줄)")
    void locksRequesterAccountThenOrganizer() {
        approve(scene.accountRequest);

        assertThat(scene.rowLockLog.locks()).containsExactly("계정 " + YEOREUM + " FOR KEY SHARE",
                "주최자 " + scene.lateSummer + " FOR UPDATE");
        assertThat(scene.staffAccountRepository.lockedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("요청한 뒤 소유자를 넘겼으면 승인하지 못한다 (CHANGE_REQUESTER_NOT_OWNER) — 주최자 값 · 기록 · 메일이 남지 않는다")
    void requesterMustStillBeOwner() {
        scene.transferOwnershipToDohyun();

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.CHANGE_REQUESTER_NOT_OWNER,
                () -> approve(scene.accountRequest));

        assertNothingApproved();
        assertThat(scene.changeRequestRepository.reviewSaves()).isZero();
    }

    @Test
    @DisplayName("계정 줄을 기다리는 사이 소유자를 넘겼으면 잡은 뒤 읽은 명단으로 막는다 (CHANGE_REQUESTER_NOT_OWNER)")
    void ownershipTransferredWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(scene::transferOwnershipToDohyun);

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.CHANGE_REQUESTER_NOT_OWNER,
                () -> approve(scene.accountRequest));

        assertNothingApproved();
    }

    @Test
    @DisplayName("이미 승인한 요청을 다시 승인하면 409 ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED이고 기록 · 메일이 하나뿐이다")
    void cannotApproveTwice() {
        approve(scene.accountRequest);

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED,
                () -> approve(scene.accountRequest));

        assertThat(scene.recordRepository.records()).hasSize(1);
        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
    }

    @Test
    @DisplayName("주최자 줄을 기다리는 사이 다른 관리자가 먼저 반려했으면 다시 읽은 요청으로 막는다 (ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED)")
    void rejectedWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(
                () -> scene.changeRequestRepository.rejectedByAnotherAdmin(scene.accountRequest, admin.id(), NOW));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED,
                () -> approve(scene.accountRequest));

        assertNothingApproved();
    }

    @Test
    @DisplayName("다시 읽은 뒤 저장 직전에 다른 관리자가 먼저 처리했으면 조건부 저장이 409로 막는다 — 주최자 값은 저장하지 않는다")
    void conditionalSaveStopsLateApproval() {
        scene.changeRequestRepository.letAnotherAdminRejectBeforeNextSave(scene.accountRequest, admin.id(), NOW);

        assertRejectedWith(ConflictException.class, OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED,
                () -> approve(scene.accountRequest));

        assertNothingApproved();
    }

    @Test
    @DisplayName("없는 요청이면 404 ORGANIZER_CHANGE_REQUEST_NOT_FOUND이고 아무 줄도 잠그지 않는다")
    void unknownRequest() {
        assertRejectedWith(NotFoundException.class, OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND,
                () -> approve(scene.accountRequest + 100));

        assertThat(scene.rowLockLog.locks()).isEmpty();
        assertNothingApproved();
    }
}
