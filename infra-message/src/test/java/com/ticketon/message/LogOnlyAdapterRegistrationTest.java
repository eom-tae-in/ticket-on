package com.ticketon.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.application.notification.port.out.SmsSendPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 실행 모듈은 com.ticketon 전체를 스캔해 runtimeOnly로 묶인 어댑터를 포트에 연결한다(TicketOnApiApplication).
 * 이 모듈만 스캔해도 두 발송 포트가 '기록만' 어댑터로 채워지는지 본다.
 */
@DisplayName("'기록만' 어댑터의 빈 등록")
class LogOnlyAdapterRegistrationTest {

    @Test
    @DisplayName("이메일·문자 발송 포트를 각각 '기록만' 어댑터가 구현한다")
    void portsAreImplementedByLogOnlyAdapters() {
        try (var context = new AnnotationConfigApplicationContext("com.ticketon.message")) {
            assertThat(context.getBean(EmailSendPort.class)).isInstanceOf(LogOnlyEmailAdapter.class);
            assertThat(context.getBean(SmsSendPort.class)).isInstanceOf(LogOnlySmsAdapter.class);
        }
    }
}
