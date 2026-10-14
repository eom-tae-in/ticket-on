package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.NOTIFICATION_SEND_RECORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.notification.NotificationChannel;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationSendRecord;
import com.ticketon.domain.notification.SendResult;
import com.ticketon.persistence.jooq.tables.records.NotificationSendRecordRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V3)을 올리고 발송 이력을 남긴다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("발송 이력 저장소 (진짜 PostgreSQL)")
class NotificationSendRecordPersistenceAdapterTest {

    private static final Instant SENT_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 12, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private NotificationSendRecordPersistenceAdapter adapter;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @BeforeEach
    void clear() {
        dsl.truncate(NOTIFICATION_SEND_RECORD).execute();
        adapter = new NotificationSendRecordPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("이메일 성공과 문자 실패가 수단마다 한 줄씩 남고, 실패 줄에는 사유가 있다")
    void storesOneRowPerChannel() {
        adapter.add(new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED, NotificationChannel.EMAIL,
                "seoyun@example.com", SendResult.success(), SENT_AT));
        adapter.add(new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED, NotificationChannel.SMS,
                "01012345678", SendResult.failure("없는 번호"), SENT_AT));

        List<NotificationSendRecordRecord> rows = dsl.selectFrom(NOTIFICATION_SEND_RECORD)
                .orderBy(NOTIFICATION_SEND_RECORD.ID)
                .fetch();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getKind()).isEqualTo("BOOKING_COMPLETED");
        assertThat(rows.get(0).getChannel()).isEqualTo("EMAIL");
        assertThat(rows.get(0).getRecipientAddress()).isEqualTo("seoyun@example.com");
        assertThat(rows.get(0).getSucceeded()).isTrue();
        assertThat(rows.get(0).getFailureReason()).isNull();
        assertThat(rows.get(0).getSentAt().toInstant()).isEqualTo(SENT_AT);
        assertThat(rows.get(1).getChannel()).isEqualTo("SMS");
        assertThat(rows.get(1).getRecipientAddress()).isEqualTo("01012345678");
        assertThat(rows.get(1).getSucceeded()).isFalse();
        assertThat(rows.get(1).getFailureReason()).isEqualTo("없는 번호");
    }

    @Test
    @DisplayName("실패인데 사유가 없는 줄은 표가 받지 않는다")
    void failureWithoutReasonRejected() {
        assertThatThrownBy(() -> dsl.insertInto(NOTIFICATION_SEND_RECORD)
                .set(NOTIFICATION_SEND_RECORD.KIND, "BOOKING_COMPLETED")
                .set(NOTIFICATION_SEND_RECORD.CHANNEL, "SMS")
                .set(NOTIFICATION_SEND_RECORD.RECIPIENT_ADDRESS, "01012345678")
                .set(NOTIFICATION_SEND_RECORD.SUCCEEDED, false)
                .set(NOTIFICATION_SEND_RECORD.SENT_AT, OffsetDateTime.of(2026, 10, 15, 3, 0, 0, 0, ZoneOffset.UTC))
                .execute())
                .isInstanceOf(DataAccessException.class);
    }
}
