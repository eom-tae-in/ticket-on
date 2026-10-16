package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.domain.error.RuleViolationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * 행사 테스트에서 함께 쓰는 값과 확인.
 */
final class EventFixtures {

    private EventFixtures() {
    }

    /** 규칙 위반으로 막히고, 그 오류 코드가 기대한 것인지 본다. */
    static void assertRejected(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
