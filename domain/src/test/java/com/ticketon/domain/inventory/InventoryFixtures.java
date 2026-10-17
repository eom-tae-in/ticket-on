package com.ticketon.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** 재고 시험에서 함께 쓰는 확인 도구. */
final class InventoryFixtures {

    private InventoryFixtures() {
    }

    /** 규칙 위반으로 그 오류 코드와 함께 막히는지. */
    static void assertRejected(ThrowingCallable action, InventoryErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    /** 충돌(다른 요청에 짐)로 그 오류 코드와 함께 막히는지. */
    static void assertConflict(ThrowingCallable action, InventoryErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
