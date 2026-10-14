package com.ticketon.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("보낼 이메일·문자")
class MessageTest {

    private static final EmailSubject SUBJECT = EmailSubject.notice(RecipientSide.AUDIENCE, "예매가 완료되었습니다");

    @Nested
    @DisplayName("이메일")
    class Email {

        @Test
        @DisplayName("받는 주소·제목·본문을 담는다")
        void holdsRecipientSubjectAndBody() {
            EmailMessage message = new EmailMessage("seoyun@example.com", SUBJECT, "예매번호를 확인하세요.");

            assertThat(message.recipientAddress()).isEqualTo("seoyun@example.com");
            assertThat(message.subject().value()).isEqualTo("예매가 완료되었습니다");
            assertThat(message.body()).isEqualTo("예매번호를 확인하세요.");
        }

        @Test
        @DisplayName("받는 주소나 본문이 비어 있으면 거부한다")
        void blankRecipientOrBodyRejected() {
            assertThatThrownBy(() -> new EmailMessage(" ", SUBJECT, "본문"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EmailMessage("seoyun@example.com", SUBJECT, ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("받는 주소·제목·본문이 없으면 거부한다")
        void missingPartsRejected() {
            assertThatThrownBy(() -> new EmailMessage(null, SUBJECT, "본문"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new EmailMessage("seoyun@example.com", null, "본문"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new EmailMessage("seoyun@example.com", SUBJECT, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("문자")
    class Sms {

        @Test
        @DisplayName("받는 번호·본문을 담는다")
        void holdsRecipientAndBody() {
            SmsMessage message = new SmsMessage("010-1234-5678", "내일 공연입니다. QR을 미리 저장해 두세요.");

            assertThat(message.recipientPhoneNumber()).isEqualTo("010-1234-5678");
            assertThat(message.body()).isEqualTo("내일 공연입니다. QR을 미리 저장해 두세요.");
        }

        @Test
        @DisplayName("받는 번호나 본문이 비어 있으면 거부한다")
        void blankRecipientOrBodyRejected() {
            assertThatThrownBy(() -> new SmsMessage("", "본문"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SmsMessage("010-1234-5678", " "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("받는 번호나 본문이 없으면 거부한다")
        void missingPartsRejected() {
            assertThatThrownBy(() -> new SmsMessage(null, "본문"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new SmsMessage("010-1234-5678", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
