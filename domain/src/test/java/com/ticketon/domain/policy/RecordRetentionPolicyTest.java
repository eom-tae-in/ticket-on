package com.ticketon.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("정책 숫자 — 보관 기간 (docs/99 C)")
class RecordRetentionPolicyTest {

    private static final LocalDate EVENT_END_DATE = LocalDate.of(2026, 3, 15);

    @Test
    @DisplayName("변경 이력은 행사 종료 후 6개월 보관한다 (docs/03)")
    void changeHistoryRetention() {
        assertThat(RecordRetentionPolicy.CHANGE_HISTORY_RETENTION_AFTER_EVENT_END).isEqualTo(Period.ofMonths(6));
        assertThat(EVENT_END_DATE.plus(RecordRetentionPolicy.CHANGE_HISTORY_RETENTION_AFTER_EVENT_END))
                .isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    @DisplayName("입장 기록은 행사 종료 30일 후 삭제한다 (docs/10)")
    void entryRecordRetention() {
        assertThat(RecordRetentionPolicy.ENTRY_RECORD_RETENTION_AFTER_EVENT_END).isEqualTo(Period.ofDays(30));
        assertThat(EVENT_END_DATE.plus(RecordRetentionPolicy.ENTRY_RECORD_RETENTION_AFTER_EVENT_END))
                .isEqualTo(LocalDate.of(2026, 4, 14));
    }

    @Test
    @DisplayName("부가 기록은 90일 후 파기한다 (docs/11)")
    void auxiliaryRecordRetention() {
        assertThat(RecordRetentionPolicy.AUXILIARY_RECORD_RETENTION).isEqualTo(Period.ofDays(90));
    }

    @Test
    @DisplayName("법정 보존은 계약·결제 5년, 분쟁처리 3년이다 (docs/11)")
    void legalRetention() {
        assertThat(RecordRetentionPolicy.LEGAL_TRANSACTION_RECORD_RETENTION).isEqualTo(Period.ofYears(5));
        assertThat(RecordRetentionPolicy.LEGAL_DISPUTE_RECORD_RETENTION).isEqualTo(Period.ofYears(3));
    }
}
