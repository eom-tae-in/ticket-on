package com.ticketon.application.admin.service;

import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.LATE_SUMMER_DETAILS;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RejectionReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 관리자가 늦여름 대표 최여름의 변경 요청을 사유를 붙여 반려한다({@link OrganizerChangeRequestScene}). */
@DisplayName("정산 계좌 · 사업자 정보 변경 반려 유스케이스")
class RejectOrganizerChangeRequestServiceTest {

    private static final RejectionReason REASON = RejectionReason.of("예금주가 대표자 이름과 다릅니다. 통장 사본을 보내 주세요.");

    private final OrganizerChangeRequestScene scene = new OrganizerChangeRequestScene();
    private final RejectOrganizerChangeRequestService service = scene.rejectService();
    private final AccountRef admin = scene.storeAdmin(AdminGrade.ADMIN);

    private void reject(long requestId) {
        service.reject(new RejectOrganizerChangeRequestCommand(admin, requestId, REASON));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AdminGrade.class)
    @DisplayName("반려하면 요청은 반려 · 처리 일시 · 처리한 관리자 · 사유를 갖고, 주최자 값은 그대로다 — 관리자 · 최고 관리자 모두 한다")
    void rejectionKeepsCurrentValues(AdminGrade grade) {
        AccountRef reviewer = scene.storeAdmin(grade);

        service.reject(new RejectOrganizerChangeRequestCommand(reviewer, scene.accountRequest, REASON));

        assertThat(scene.changeRequestRepository.findById(scene.accountRequest).orElseThrow()).satisfies(request -> {
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.REJECTED);
            assertThat(request.reviewedAt()).contains(NOW);
            assertThat(request.reviewerAdminAccountId()).hasValue(reviewer.id());
            assertThat(request.rejectionReason()).contains(REASON);
        });
        assertThat(scene.organizerRepository.findBusinessDetails(scene.lateSummer)).contains(LATE_SUMMER_DETAILS);
        assertThat(scene.organizerRepository.businessDetailsSaves()).isZero();
    }

    @Test
    @DisplayName("관리자 조작 기록에 사유와 함께 남는다 — 요약은 '사업자 정보 변경 반려 — 주최자명'")
    void leavesOperationRecordWithReason() {
        reject(scene.businessRequest);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.kind()).isEqualTo(AdminOperationKind.REJECT_ORGANIZER_CHANGE_REQUEST);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_CHANGE_REQUEST);
            assertThat(record.targetId()).isEqualTo(scene.businessRequest);
            assertThat(record.summary()).isEqualTo("사업자 정보 변경 반려 — 늦여름");
            assertThat(record.reason()).hasValue(REASON.text());
        });
    }

    @Test
    @DisplayName("사유를 담은 결과 메일([주최])이 소유자에게 간다 — 지금 계좌가 그대로 쓰인다고 알린다")
    void notifiesOwnerWithReason() {
        reject(scene.accountRequest);

        assertThat(scene.sendNotificationUseCase.sentCommands()).singleElement().satisfies(sent -> {
            assertThat(sent.kind()).isEqualTo(NotificationKind.SETTLEMENT_ACCOUNT_CHANGE_DECIDED);
            assertThat(sent.recipient()).isEqualTo(NotificationRecipient.organizer(scene.lateSummer));
            assertThat(sent.content().title()).isEqualTo("'늦여름' 정산 계좌 변경이 반려되었습니다");
            assertThat(sent.content().body()).contains("지금 정산 계좌가 그대로 쓰입니다.", "반려 사유: " + REASON.text());
        });
    }

    @Test
    @DisplayName("요청한 뒤 소유자를 넘겼어도 반려는 한다 — 운영진 계정 줄 · 주최자 줄을 잠그지 않는다")
    void rejectsWithoutLockingAfterOwnershipTransfer() {
        scene.transferOwnershipToDohyun();

        reject(scene.accountRequest);

        assertThat(scene.changeRequestRepository.findById(scene.accountRequest).orElseThrow().status())
                .isEqualTo(OrganizerChangeRequestStatus.REJECTED);
        assertThat(scene.rowLockLog.locks()).isEmpty();
    }

    @Test
    @DisplayName("이미 처리한 요청은 반려하지 못한다 (ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED)")
    void cannotRejectTwice() {
        reject(scene.accountRequest);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> reject(scene.accountRequest))
                .satisfies(rejected -> assertThat(rejected.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED));
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("읽은 뒤 다른 관리자가 먼저 처리했으면 조건부 저장이 409로 막고 기록 · 메일이 남지 않는다")
    void conditionalSaveStopsLateRejection() {
        scene.changeRequestRepository.letAnotherAdminRejectBeforeNextSave(scene.accountRequest, admin.id(), NOW);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> reject(scene.accountRequest))
                .satisfies(conflict -> assertThat(conflict.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED));
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("없는 요청이면 404 ORGANIZER_CHANGE_REQUEST_NOT_FOUND")
    void unknownRequest() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> reject(scene.accountRequest + 100))
                .satisfies(notFound -> assertThat(notFound.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND));
    }
}
