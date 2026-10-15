package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 극단 열두달(주최자 12)의 운영진 조작 기록 — 대표 박정훈(소유자)이 주최자명을 바꾸고, 기획 단원(운영자)이 공개 정보를 고친다.
 */
@DisplayName("운영진 조작 기록 (docs/01 \"누가 했는지 남깁니다\")")
class StaffOperationRecordTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;

    private static final Instant OPERATED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 14, 30));

    private static final StaffPermission PLANNER_EDITS_PROFILE =
            new StaffPermission(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE);

    @Test
    @DisplayName("확인한 권한 그대로 주최자 · 운영진 · 그때의 역할 · 한 일이 남고, 대상 · 요약 · 시각이 함께 남는다")
    void recordsWhoWhenWhat() {
        StaffOperationRecord record = StaffOperationRecord.of(PLANNER_EDITS_PROFILE, StaffOperationTargetKind.ORGANIZER,
                TWELVE_MONTHS, "주최자 소개 · SNS 링크 수정", OPERATED_AT);

        assertThat(record.organizerId()).isEqualTo(TWELVE_MONTHS);
        assertThat(record.staffAccountId()).isEqualTo(PLANNER);
        assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
        assertThat(record.action()).isEqualTo(StaffAction.EDIT_PUBLIC_PROFILE);
        assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
        assertThat(record.targetId()).isEqualTo(TWELVE_MONTHS);
        assertThat(record.summary()).isEqualTo("주최자 소개 · SNS 링크 수정");
        assertThat(record.operatedAt()).isEqualTo(OPERATED_AT);
    }

    @Test
    @DisplayName("소유자만 하는 일은 소유자 역할로 남는다")
    void ownerOnlyActionKeepsOwnerRole() {
        StaffOperationRecord record = StaffOperationRecord.of(
                new StaffPermission(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, StaffAction.RENAME_ORGANIZER),
                StaffOperationTargetKind.ORGANIZER, TWELVE_MONTHS, "주최자명 변경 — 극단 열두달 → 열두달 레퍼토리", OPERATED_AT);

        assertThat(record.staffRole()).isEqualTo(StaffRole.OWNER);
        assertThat(record.action()).isEqualTo(StaffAction.RENAME_ORGANIZER);
    }

    @Test
    @DisplayName("요약의 앞뒤 공백(전각 공백 포함)을 걷는다")
    void stripsSummary() {
        StaffOperationRecord record = StaffOperationRecord.of(PLANNER_EDITS_PROFILE, StaffOperationTargetKind.ORGANIZER,
                TWELVE_MONTHS, "　 주최자 로고 변경 \t", OPERATED_AT);

        assertThat(record.summary()).isEqualTo("주최자 로고 변경");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "　"})
    @DisplayName("요약이 비면 남기지 않는다 — 목록에서 무엇을 했는지 읽을 수 없다")
    void rejectsBlankSummary(String summary) {
        assertThatIllegalArgumentException().isThrownBy(() -> StaffOperationRecord.of(PLANNER_EDITS_PROFILE,
                StaffOperationTargetKind.ORGANIZER, TWELVE_MONTHS, summary, OPERATED_AT));
    }

    @Test
    @DisplayName("대상이 주최자 자신이면 기록이 걸린 주최자여야 한다 — 열두달의 이력에 다른 주최자를 바꾼 기록이 끼지 않게")
    void organizerTargetMustBeOwnOrganizer() {
        assertThatIllegalArgumentException().isThrownBy(() -> StaffOperationRecord.of(PLANNER_EDITS_PROFILE,
                StaffOperationTargetKind.ORGANIZER, 20L, "주최자 소개 수정", OPERATED_AT));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {0, -1})
    @DisplayName("대상 id는 1 이상이다")
    void rejectsInvalidTargetId(long targetId) {
        assertThatIllegalArgumentException().isThrownBy(() -> StaffOperationRecord.of(PLANNER_EDITS_PROFILE,
                StaffOperationTargetKind.ORGANIZER, targetId, "주최자 소개 수정", OPERATED_AT));
    }
}
