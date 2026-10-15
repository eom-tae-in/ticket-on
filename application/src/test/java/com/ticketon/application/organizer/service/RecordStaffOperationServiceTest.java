package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.out.StaffOperationRecordRepository;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationRecord;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffPermission;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(주최자 12)의 기획 단원(운영자)이 공개 정보를 고친 뒤 남기는 기록. 트랜잭션 밖에서 막히는지는 Spring 트랜잭션 프록시를 건 테스트
 * (app-api)가 본다.
 */
@DisplayName("운영진 조작 기록 유스케이스")
class RecordStaffOperationServiceTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long PLANNER = 2L;

    private static final Instant OPERATED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 14, 30));

    private static final StaffPermission PLANNER_EDITS_PROFILE =
            new StaffPermission(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE);

    private final RecordingRepository recordRepository = new RecordingRepository();
    private final RecordStaffOperationService service = new RecordStaffOperationService(recordRepository);

    @Test
    @DisplayName("확인한 권한 그대로 누가 · 그때의 역할 · 한 일과 대상 · 요약 · 시각을 한 줄 남긴다")
    void recordsOneLine() {
        service.record(new RecordStaffOperationCommand(PLANNER_EDITS_PROFILE, StaffOperationTargetKind.ORGANIZER,
                TWELVE_MONTHS, "주최자 소개 · SNS 링크 수정", OPERATED_AT));

        assertThat(recordRepository.records).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(TWELVE_MONTHS);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_PUBLIC_PROFILE);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
            assertThat(record.targetId()).isEqualTo(TWELVE_MONTHS);
            assertThat(record.summary()).isEqualTo("주최자 소개 · SNS 링크 수정");
            assertThat(record.operatedAt()).isEqualTo(OPERATED_AT);
        });
    }

    @Test
    @DisplayName("요약이 비면 부르는 쪽의 잘못으로 막고 아무것도 남기지 않는다")
    void rejectsBlankSummary() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.record(new RecordStaffOperationCommand(
                PLANNER_EDITS_PROFILE, StaffOperationTargetKind.ORGANIZER, TWELVE_MONTHS, " ", OPERATED_AT)));

        assertThat(recordRepository.records).isEmpty();
    }

    private static final class RecordingRepository implements StaffOperationRecordRepository {

        private final List<StaffOperationRecord> records = new ArrayList<>();

        @Override
        public void add(StaffOperationRecord record) {
            records.add(record);
        }
    }
}
