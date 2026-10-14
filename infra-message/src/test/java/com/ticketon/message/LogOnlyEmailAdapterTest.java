package com.ticketon.message;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.EmailSubject;
import com.ticketon.domain.notification.RecipientSide;
import com.ticketon.domain.notification.SendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("이메일 '기록만' 어댑터")
class LogOnlyEmailAdapterTest {

    private final LogOnlyEmailAdapter adapter = new LogOnlyEmailAdapter();
    private CapturedLogs logs;

    @BeforeEach
    void captureLogs() {
        logs = CapturedLogs.of(LogOnlyEmailAdapter.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
    }

    @Test
    @DisplayName("받는 곳·제목·본문을 로그로 남기고 성공을 돌려준다")
    void logsMessageAndSucceeds() {
        EmailMessage message = new EmailMessage(
                "jeonghun@example.com",
                EmailSubject.notice(RecipientSide.ORGANIZER, "주최자 신청이 승인되었습니다"),
                "이제 행사를 열 수 있습니다.");

        SendResult result = adapter.send(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .contains("jeonghun@example.com")
                    .contains("[주최] 주최자 신청이 승인되었습니다")
                    .contains("이제 행사를 열 수 있습니다.");
        });
    }

    @Test
    @DisplayName("보낼 때마다 한 줄씩 남긴다")
    void logsEachMessage() {
        EmailSubject subject = EmailSubject.notice(RecipientSide.AUDIENCE, "예매가 완료되었습니다");

        adapter.send(new EmailMessage("seoyun@example.com", subject, "첫 번째"));
        adapter.send(new EmailMessage("dohyun@example.com", subject, "두 번째"));

        assertThat(logs.events()).extracting(ILoggingEvent::getFormattedMessage)
                .satisfiesExactly(
                        first -> assertThat(first).contains("seoyun@example.com", "첫 번째"),
                        second -> assertThat(second).contains("dohyun@example.com", "두 번째"));
    }
}
