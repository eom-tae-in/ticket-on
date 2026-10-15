package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OwnershipChange;
import com.ticketon.domain.organizer.OwnershipTransfer;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 극단 열두달의 대표 박정훈(1)이 기획 단원(2)에게 소유자를 넘긴 한 건을 관리자 이도현이 처리 목록에서 확인한다. */
@DisplayName("소유자 변경 사후 확인 유스케이스 (docs/12 \"소유자가 바뀌면 관리자에게도 알립니다\")")
class ConfirmOwnershipChangeServiceTest {

    private static final Instant TRANSFERRED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 14, 0);

    private final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    private final FakeOwnershipChangeRepository ownershipChangeRepository = new FakeOwnershipChangeRepository();
    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();
    private final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    private final MutableClock clock = MutableClock.at(NOW);

    private final ConfirmOwnershipChangeService service = new ConfirmOwnershipChangeService(adminAccountRepository,
            ownershipChangeRepository, organizerRepository, new AdminOperationRecorder(recordRepository), clock);

    private long changeId;

    @BeforeEach
    void storeChange() {
        long organizerId = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE);
        changeId = ownershipChangeRepository.add(new OwnershipTransfer(organizerId, 1, 2, TRANSFERRED_AT)).id();
    }

    private AccountRef storeAdmin(AdminGrade grade) {
        long id = adminAccountRepository.store(grade.name().toLowerCase() + "@ticket-on.example",
                FakePasswordHashPort.hashOf("admin-1234"), grade, TRANSFERRED_AT.minusSeconds(86_400));
        return AccountRef.of(AccountKind.ADMIN, id);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AdminGrade.class)
    @DisplayName("관리자 · 최고 관리자 모두 확인하고, 확인하면 확인 일시 · 확인한 관리자가 남고 조작 기록이 남는다")
    void confirms(AdminGrade grade) {
        AccountRef admin = storeAdmin(grade);

        service.confirm(new ConfirmOwnershipChangeCommand(admin, changeId));

        OwnershipChange saved = ownershipChangeRepository.findById(changeId).orElseThrow();
        assertThat(saved.confirmedAt()).contains(clock.instant());
        assertThat(saved.confirmedBy()).contains(admin);
        assertThat(recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.operator()).isEqualTo(admin);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.CONFIRM_OWNERSHIP_CHANGE);
            assertThat(record.targetId()).isEqualTo(changeId);
            assertThat(record.summary()).isEqualTo("소유자 변경 확인 — 극단 열두달");
            assertThat(record.operatedAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    @DisplayName("이미 확인한 변경은 다시 확인하지 못하고(OWNERSHIP_CHANGE_ALREADY_CONFIRMED) 기록도 더 남지 않는다")
    void confirmsOnlyOnce() {
        AccountRef admin = storeAdmin(AdminGrade.ADMIN);
        service.confirm(new ConfirmOwnershipChangeCommand(admin, changeId));

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.confirm(new ConfirmOwnershipChangeCommand(admin, changeId)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED));
        assertThat(recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("없는 소유자 변경 이력이면 404 OWNERSHIP_CHANGE_NOT_FOUND다")
    void unknownChange() {
        AccountRef admin = storeAdmin(AdminGrade.ADMIN);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.confirm(new ConfirmOwnershipChangeCommand(admin, changeId + 1)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CHANGE_NOT_FOUND));
        assertThat(recordRepository.records()).isEmpty();
    }
}
