package com.ticketon.domain.notification;

import java.util.Objects;

/**
 * 이메일·문자 한 통의 발송 결과 — 성공, 또는 실패와 그 사유.
 * <p>
 * 발송은 실패해도 예외로 흐름을 끊지 않고 이 값으로 알린다. 문자가 잘못된 번호로 가 실패해도 알림함에는 그대로 남아야 하고
 * (docs/12 "전화번호는 인증하지 않습니다"), 긴급 통지는 이메일과 문자를 모두 보내 한쪽이 실패해도 다른 쪽은 전달돼야 하기 때문이다.
 */
public sealed interface SendResult permits SendResult.Success, SendResult.Failure {

    static SendResult success() {
        return new Success();
    }

    /**
     * @param reason 실패 사유(업체가 알려 준 내용 등). 발송 이력에 남길 수 있게 비워 두지 않는다.
     */
    static SendResult failure(String reason) {
        return new Failure(reason);
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    /** 발송했다. */
    record Success() implements SendResult {
    }

    /** 발송하지 못했다. */
    record Failure(String reason) implements SendResult {

        public Failure {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("발송 실패 사유가 비어 있습니다");
            }
        }
    }
}
