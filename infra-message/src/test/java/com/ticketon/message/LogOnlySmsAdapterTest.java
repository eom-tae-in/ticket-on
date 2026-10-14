package com.ticketon.message;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.ticketon.domain.notification.SendResult;
import com.ticketon.domain.notification.SmsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("문자 '기록만' 어댑터")
class LogOnlySmsAdapterTest {

    private final LogOnlySmsAdapter adapter = new LogOnlySmsAdapter();
    private CapturedLogs logs;

    @BeforeEach
    void captureLogs() {
        logs = CapturedLogs.of(LogOnlySmsAdapter.class);
    }

    @AfterEach
    void releaseLogs() {
        logs.close();
    }

    @Test
    @DisplayName("받는 번호·본문을 로그로 남기고 성공을 돌려준다")
    void logsMessageAndSucceeds() {
        SmsMessage message = new SmsMessage("010-1234-5678", "내일 공연입니다. QR을 미리 저장해 두세요.");

        SendResult result = adapter.send(message);

        assertThat(result.isSuccess()).isTrue();
        assertThat(logs.events()).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage())
                    .contains("010-1234-5678")
                    .contains("내일 공연입니다. QR을 미리 저장해 두세요.");
        });
    }
}
