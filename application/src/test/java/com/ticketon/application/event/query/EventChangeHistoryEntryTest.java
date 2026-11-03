package com.ticketon.application.event.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.event.EventChangeHistoryTarget;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(docs/15 박정훈)의 봄 정기공연 — 3/1 10:00에 판매를 시작하며 공개용 연락처 "twelve-months@example.com"을 첫 버전으로 쌓았고, 3/10
 * 14:00에 "010-1234-5678"로 바꿨다.
 */
@DisplayName("행사 변경 이력 조회 한 줄")
class EventChangeHistoryEntryTest {

    private static final Instant PUBLISHED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 1, 10, 0));
    private static final Instant CHANGED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 10, 14, 0));

    private static EventChangeHistoryEntry entry(EventChangeHistoryTarget target, String previousContent, String newContent) {
        return new EventChangeHistoryEntry(2, target, null, null, null, previousContent, newContent, CHANGED_AT, null, 7, "푸른바람",
                false);
    }

    @Test
    @DisplayName("공개용 연락처 줄은 글로 보일 때 바꾸기 전 · 뒤 연락처를 가린다 — 같은지는 값으로 본다")
    void publicContactIsMaskedInText() {
        EventChangeHistoryEntry contactChange = entry(EventChangeHistoryTarget.PUBLIC_CONTACT, "twelve-months@example.com",
                "010-1234-5678");

        assertThat(contactChange.toString()).doesNotContain("twelve-months@example.com").doesNotContain("010-1234-5678")
                .contains("PUBLIC_CONTACT", "previousContent=****", "newContent=****", "푸른바람");
        assertThat(entry(EventChangeHistoryTarget.PUBLIC_CONTACT, "twelve-months@example.com", null).toString())
                .contains("newContent=null");
        assertThat(contactChange).isEqualTo(entry(EventChangeHistoryTarget.PUBLIC_CONTACT, "twelve-months@example.com",
                "010-1234-5678"));
        assertThat(contactChange.newContent()).isEqualTo("010-1234-5678");
    }

    @Test
    @DisplayName("다른 칸은 내용을 그대로 싣는다 — 상세 안내 \"지하 1층\" → \"지하 1층, 주차 불가\"")
    void otherTargetsShowContent() {
        assertThat(entry(EventChangeHistoryTarget.VENUE_DETAILS, "지하 1층", "지하 1층, 주차 불가").toString())
                .contains("previousContent=지하 1층", "newContent=지하 1층, 주차 불가");
    }

    @Test
    @DisplayName("유효 기간은 [시작, 끝)이다 — 3/10 14:00에 닫힌 첫 버전은 3/5에 보였고 3/10 14:00에는 보이지 않았다")
    void validPeriod() {
        EventChangeHistoryEntry first = new EventChangeHistoryEntry(1, EventChangeHistoryTarget.PUBLIC_CONTACT, null, null, null, null,
                "twelve-months@example.com", PUBLISHED_AT, CHANGED_AT, 7, "푸른바람", false);

        assertThat(first.isValidAt(KoreanTime.toInstant(LocalDateTime.of(2026, 3, 5, 20, 0)))).isTrue();
        assertThat(first.isValidAt(CHANGED_AT)).isFalse();
    }
}
