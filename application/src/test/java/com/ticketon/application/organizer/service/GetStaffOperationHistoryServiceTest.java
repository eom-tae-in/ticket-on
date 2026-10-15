package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.organizer.port.in.GetStaffOperationHistoryQuery;
import com.ticketon.application.organizer.port.in.StaffOperationHistoryResult;
import com.ticketon.application.organizer.port.in.StaffOperationHistoryResult.Entry;
import com.ticketon.application.organizer.port.out.StaffOperationHistoryQueryPort;
import com.ticketon.application.organizer.query.StaffOperationHistoryEntry;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(주최자 12)에 기록이 셋 있다 — 박정훈(소유자)의 주최자명 변경 둘과 기획 단원(운영자)의 공개 정보 수정 하나.
 */
@DisplayName("주최자 이력의 운영진 조작 기록 조회 유스케이스")
class GetStaffOperationHistoryServiceTest {

    private static final long TWELVE_MONTHS = 12L;

    private static final Instant FIRST_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final List<StaffOperationHistoryEntry> STORED = List.of(
            entry(1, 1L, "조용한고래", "박정훈", StaffRole.OWNER, StaffAction.RENAME_ORGANIZER,
                    "주최자명 변경 — 극단 열두달 → 열두달 레퍼토리"),
            entry(2, 2L, "푸른바람", "김기획", StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE, "주최자 소개 · SNS 링크 수정"),
            entry(3, 1L, "조용한고래", "박정훈", StaffRole.OWNER, StaffAction.RENAME_ORGANIZER,
                    "주최자명 변경 — 열두달 레퍼토리 → 극단 열두달"));

    private final FakeHistoryQueryPort historyQueryPort = new FakeHistoryQueryPort();
    private final GetStaffOperationHistoryService service = new GetStaffOperationHistoryService(historyQueryPort);

    private static StaffOperationHistoryEntry entry(long recordId, long staffAccountId, String nickname, String name,
            StaffRole role, StaffAction action, String summary) {
        return new StaffOperationHistoryEntry(recordId, FIRST_AT.plusSeconds(recordId * 60), staffAccountId, nickname,
                name, role, action, StaffOperationTargetKind.ORGANIZER, TWELVE_MONTHS, summary);
    }

    @Test
    @DisplayName("최근 것부터 누가 · 언제 · 그때의 역할 · 무엇을을 옮기고, 더 앞의 기록이 없으면 이어 보기 값이 없다")
    void listsAllWhenFitsInOnePage() {
        StaffOperationHistoryResult result = service.getHistory(new GetStaffOperationHistoryQuery(TWELVE_MONTHS, null, 10));

        assertThat(result.entries()).extracting(Entry::recordId).containsExactly(3L, 2L, 1L);
        assertThat(result.entries().get(1)).isEqualTo(new Entry(2, FIRST_AT.plusSeconds(120), 2L, "푸른바람", "김기획",
                StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE, StaffOperationTargetKind.ORGANIZER, TWELVE_MONTHS,
                "주최자 소개 · SNS 링크 수정"));
        assertThat(result.nextBeforeRecordId()).isNull();
    }

    @Test
    @DisplayName("더 앞의 기록이 있으면 이번 마지막 기록의 id로 이어 보고, 마지막 쪽에서는 이어 보기 값이 없다")
    void continuesWithNextBeforeRecordId() {
        StaffOperationHistoryResult firstPage =
                service.getHistory(new GetStaffOperationHistoryQuery(TWELVE_MONTHS, null, 2));
        StaffOperationHistoryResult secondPage = service.getHistory(
                new GetStaffOperationHistoryQuery(TWELVE_MONTHS, firstPage.nextBeforeRecordId(), 2));

        assertThat(firstPage.entries()).extracting(Entry::recordId).containsExactly(3L, 2L);
        assertThat(firstPage.nextBeforeRecordId()).isEqualTo(2L);
        assertThat(secondPage.entries()).extracting(Entry::recordId).containsExactly(1L);
        assertThat(secondPage.nextBeforeRecordId()).isNull();
    }

    @Test
    @DisplayName("기록 수가 정확히 한 쪽이면 이어 보기 값이 없다 — 한 줄을 더 읽어 확인한다")
    void exactPageHasNoNext() {
        StaffOperationHistoryResult result = service.getHistory(new GetStaffOperationHistoryQuery(TWELVE_MONTHS, null, 3));

        assertThat(result.entries()).hasSize(3);
        assertThat(result.nextBeforeRecordId()).isNull();
    }

    /** 메모리 조회 — 주최자 12의 기록을 id 역순으로, 이어 보기 값보다 앞의 것만 돌려준다. */
    private static final class FakeHistoryQueryPort implements StaffOperationHistoryQueryPort {

        @Override
        public List<StaffOperationHistoryEntry> historyOf(long organizerId, Long beforeRecordId, int limit) {
            return STORED.stream()
                    .filter(entry -> organizerId == TWELVE_MONTHS)
                    .filter(entry -> beforeRecordId == null || entry.recordId() < beforeRecordId)
                    .sorted(Comparator.comparingLong(StaffOperationHistoryEntry::recordId).reversed())
                    .limit(limit)
                    .toList();
        }
    }
}
