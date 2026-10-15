package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerResult;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 대표 박정훈(소유자)이 주최자명을 "열두달 프로젝트"로 바꾼다(docs/01 "공개 페이지 — 주최자명 변경", {@link OrganizerStaffScene}).
 * 관리자 확인 없이 바로 적용되고, 이력 · 운영진 전원 메일 · 조작 기록이 남는다.
 */
@DisplayName("주최자명 변경 유스케이스")
class RenameOrganizerServiceTest {

    private static final OrganizerName TWELVE_MONTHS = OrganizerName.of("극단 열두달");

    private static final OrganizerName PROJECT = OrganizerName.of("열두달 프로젝트");

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final RenameOrganizerService service = scene.renameOrganizerService();

    private RenameOrganizerResult rename(long staffAccountId, String newName) {
        return service.rename(new RenameOrganizerCommand(acting(staffAccountId, scene.twelveMonths),
                OrganizerName.of(newName)));
    }

    private OrganizerName storedName() {
        return scene.organizerRepository.findById(scene.twelveMonths).orElseThrow().name();
    }

    private void assertNothingChanged() {
        assertThat(storedName()).isEqualTo(TWELVE_MONTHS);
        assertThat(scene.organizerRepository.nameSaves()).isZero();
        assertThat(scene.nameChangeRepository.savedEntries()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("소유자가 바꾸면 관리자 확인 없이 바로 바뀐다 — 앞뒤 공백을 걷은 이름이 저장되고 바꾼 이름을 돌려준다")
    void ownerRenamesImmediately() {
        RenameOrganizerResult result = rename(JUNGHOON, " 열두달 프로젝트 ");

        assertThat(result.organizerId()).isEqualTo(scene.twelveMonths);
        assertThat(result.name()).isEqualTo("열두달 프로젝트");
        assertThat(storedName()).isEqualTo(PROJECT);
        assertThat(scene.organizerRepository.nameSaves()).isEqualTo(1);
    }

    @Test
    @DisplayName("바꾸면 언제 · 무엇에서 무엇으로 · 누가 바꿨는지 확인 전인 이력이 남는다(관리자 처리 목록)")
    void recordsHistory() {
        rename(JUNGHOON, "열두달 프로젝트");

        assertThat(scene.nameChangeRepository.savedEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(entry.change()).isEqualTo(new OrganizerNameChange(TWELVE_MONTHS, PROJECT, JUNGHOON, NOW));
            assertThat(entry.isConfirmed()).isFalse();
        });
    }

    @Test
    @DisplayName("바꾼 소유자의 조작 기록이 주최자를 대상으로 남는다")
    void recordsOperation() {
        rename(JUNGHOON, "열두달 프로젝트");

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(JUNGHOON);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OWNER);
            assertThat(record.action()).isEqualTo(StaffAction.RENAME_ORGANIZER);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
            assertThat(record.targetId()).isEqualTo(scene.twelveMonths);
            assertThat(record.summary()).isEqualTo("주최자명 변경 · 극단 열두달 → 열두달 프로젝트");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("운영진 전원에게 '주최자명 변경' 메일을 한 번 보낸다 — 두 이름과 바꾼 사람의 닉네임을 적는다")
    void notifiesAllStaff() {
        rename(JUNGHOON, "열두달 프로젝트");

        assertThat(scene.sendNotificationUseCase.sentCommands()).singleElement().satisfies(sent -> {
            assertThat(sent.kind()).isEqualTo(NotificationKind.ORGANIZER_NAME_CHANGED);
            assertThat(sent.recipient()).isEqualTo(NotificationRecipient.organizer(scene.twelveMonths));
            assertThat(sent.content().title()).isEqualTo("주최자명이 바뀌었습니다: 극단 열두달 → 열두달 프로젝트");
            assertThat(sent.content().body()).contains("바꾸기 전: 극단 열두달", "바꾼 뒤: 열두달 프로젝트",
                    "바꾼 사람: 조용한고래 님(소유자)");
        });
    }

    @Test
    @DisplayName("잠금 순서 — 바꾸는 사람의 계정 줄에 외래 키 확인 잠금을 주최자 줄보다 먼저 건다 (계정 줄 → 주최자 줄, #195)")
    void locksChangerAccountBeforeOrganizer() {
        rename(JUNGHOON, "열두달 프로젝트");

        assertThat(scene.rowLockLog.locks()).containsExactly("계정 " + JUNGHOON + " FOR KEY SHARE",
                "주최자 " + scene.twelveMonths + " FOR UPDATE");
        assertThat(scene.staffAccountRepository.lockedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("지금 이름과 같아 아무것도 바꾸지 않아도 잠금 순서는 같다")
    void sameNameKeepsLockOrder() {
        rename(JUNGHOON, "극단 열두달");

        assertThat(scene.rowLockLog.locks()).containsExactly("계정 " + JUNGHOON + " FOR KEY SHARE",
                "주최자 " + scene.twelveMonths + " FOR UPDATE");
    }

    @Test
    @DisplayName("운영자는 바꾸지 못한다 (OWNER_ONLY_ACTION) — 이름 · 이력 · 기록 · 메일 모두 그대로다")
    void operatorCannotRename() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> rename(PLANNER, "열두달 프로젝트"))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.OWNER_ONLY_ACTION));

        assertNothingChanged();
        assertThat(scene.rowLockLog.locks()).isEmpty();
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 바꾸지 못한다 — 다른 주최자의 소유자 · 운영자도 (NOT_ORGANIZER_STAFF)")
    void outsiderCannotRename() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> rename(YEOREUM, "열두달 프로젝트"))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> rename(DOHYUN, "열두달 프로젝트"));

        assertNothingChanged();
        assertThat(scene.rowLockLog.locks()).isEmpty();
    }

    @Test
    @DisplayName("지금 이름과 같은 이름이면(앞뒤 공백만 다름) 아무것도 바꾸지 않는다 — 저장 · 이력 · 기록 · 메일이 없다")
    void sameNameLeavesNoTrace() {
        RenameOrganizerResult result = rename(JUNGHOON, "  극단 열두달　");

        assertThat(result.name()).isEqualTo("극단 열두달");
        assertNothingChanged();
    }

    @Test
    @DisplayName("두 번 바꾸면 이력이 두 줄 남고, 둘째 줄의 바꾸기 전 이름은 첫째 줄의 바꾼 이름이다")
    void renamesTwice() {
        rename(JUNGHOON, "열두달 프로젝트");
        scene.clock.advance(Duration.ofHours(1));
        rename(JUNGHOON, "극단 열세달");

        assertThat(storedName()).isEqualTo(OrganizerName.of("극단 열세달"));
        assertThat(scene.nameChangeRepository.savedEntries()).extracting(entry -> entry.change().previousName().value(),
                entry -> entry.change().newName().value()).containsExactly(
                tuple("극단 열두달", "열두달 프로젝트"),
                tuple("열두달 프로젝트", "극단 열세달"));
        assertThat(scene.sendNotificationUseCase.sentCommands()).extracting(SendNotificationCommand::kind)
                .containsExactly(NotificationKind.ORGANIZER_NAME_CHANGED, NotificationKind.ORGANIZER_NAME_CHANGED);
    }
}
