package com.ticketon.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("발송 결과")
class SendResultTest {

    @Test
    @DisplayName("성공은 성공으로 보인다")
    void successIsSuccess() {
        SendResult result = SendResult.success();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result).isInstanceOf(SendResult.Success.class).isEqualTo(SendResult.success());
    }

    @Test
    @DisplayName("실패는 사유를 담고 성공이 아니다")
    void failureHoldsReason() {
        SendResult result = SendResult.failure("없는 번호");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isInstanceOfSatisfying(SendResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo("없는 번호"));
    }

    @Test
    @DisplayName("실패 사유가 없거나 비어 있으면 거부한다")
    void missingReasonRejected() {
        assertThatThrownBy(() -> SendResult.failure(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SendResult.failure(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
