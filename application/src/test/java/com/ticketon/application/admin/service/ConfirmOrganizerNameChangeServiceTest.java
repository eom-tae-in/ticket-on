package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ConfirmOrganizerNameChangeCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.OrganizerNameChangeEntry;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 극단 열두달(12)의 대표 박정훈(1)이 주최자명을 "열두달 프로젝트"로 바꾼 한 건을 관리자 이도현이 처리 목록에서 확인한다. */
@DisplayName("주최자명 변경 사후 확인 유스케이스 (docs/12 \"관리자 대상 — 주최자명 변경 (사후 확인용)\")")
class ConfirmOrganizerNameChangeServiceTest {

    private static final Instant CHANGED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 14, 0);

    private final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    private final FakeOrganizerNameChangeRepository nameChangeRepository = new FakeOrganizerNameChangeRepository();
    private final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    private final MutableClock clock = MutableClock.at(NOW);

    private final ConfirmOrganizerNameChangeService service = new ConfirmOrganizerNameChangeService(
            adminAccountRepository, nameChangeRepository, new AdminOperationRecorder(recordRepository), clock);

    private long entryId;

    @BeforeEach
    void storeEntry() {
        entryId = nameChangeRepository.add(12, new OrganizerNameChange(OrganizerName.of("극단 열두달"),
                OrganizerName.of("열두달 프로젝트"), 1, CHANGED_AT)).id();
    }

    private AccountRef storeAdmin(AdminGrade grade) {
        long id = adminAccountRepository.store(grade.name().toLowerCase() + "@ticket-on.example",
                FakePasswordHashPort.hashOf("admin-1234"), grade, CHANGED_AT.minusSeconds(86_400));
        return AccountRef.of(AccountKind.ADMIN, id);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AdminGrade.class)
    @DisplayName("관리자 · 최고 관리자 모두 확인하고, 확인하면 확인 일시 · 확인한 관리자가 남고 조작 기록이 남는다 — 이름은 그대로다")
    void confirms(AdminGrade grade) {
        AccountRef admin = storeAdmin(grade);

        service.confirm(new ConfirmOrganizerNameChangeCommand(admin, entryId));

        OrganizerNameChangeEntry saved = nameChangeRepository.findById(entryId).orElseThrow();
        assertThat(saved.confirmedAt()).contains(clock.instant());
        assertThat(saved.confirmedBy()).contains(admin);
        assertThat(saved.change().newName()).isEqualTo(OrganizerName.of("열두달 프로젝트"));
        assertThat(recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.operator()).isEqualTo(admin);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.CONFIRM_ORGANIZER_NAME_CHANGE);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_NAME_CHANGE);
            assertThat(record.targetId()).isEqualTo(entryId);
            assertThat(record.summary()).isEqualTo("주최자명 변경 확인 — 극단 열두달 → 열두달 프로젝트");
            assertThat(record.operatedAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    @DisplayName("이미 확인한 변경은 다시 확인하지 못하고(ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED) 기록도 더 남지 않는다")
    void confirmsOnlyOnce() {
        AccountRef admin = storeAdmin(AdminGrade.ADMIN);
        service.confirm(new ConfirmOrganizerNameChangeCommand(admin, entryId));

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.confirm(new ConfirmOrganizerNameChangeCommand(admin, entryId)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED));
        assertThat(recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("없는 주최자명 변경 이력이면 404 ORGANIZER_NAME_CHANGE_NOT_FOUND다")
    void unknownEntry() {
        AccountRef admin = storeAdmin(AdminGrade.ADMIN);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.confirm(new ConfirmOrganizerNameChangeCommand(admin, entryId + 1)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_NOT_FOUND));
        assertThat(recordRepository.records()).isEmpty();
    }
}
