package com.ticketon.domain.notification;

import com.ticketon.domain.policy.RecordRetentionPolicy;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 발송 이력 한 줄 — 어느 종류를 어느 수단으로 어디에 보냈고, 성공했는지, 언제였는지.
 * <p>
 * 이메일과 문자는 수단마다 한 줄씩 남는다. 한 수단이 실패해도 다른 수단의 발송과 기록은 그대로 이어진다
 * (docs/12 "긴급 통지는 이메일과 문자를 모두 보내므로 한쪽이 실패해도 전달될 가능성이 남습니다"). 실패하면 사유를 함께 남긴다.
 * <p>
 * 부가 기록이라 {@link RecordRetentionPolicy#AUXILIARY_RECORD_RETENTION 90일} 뒤 파기한다(docs/11 "데이터 보관", 파기 작업은 #32).
 *
 * @param kind             알림 종류
 * @param channel          수단
 * @param recipientAddress 받는 곳 — 이메일 주소 또는 숫자만 남긴 전화번호
 * @param result           발송 결과
 * @param sentAt           보낸 시각
 */
public record NotificationSendRecord(NotificationKind kind, NotificationChannel channel, String recipientAddress,
        SendResult result, Instant sentAt) {

    public NotificationSendRecord {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(recipientAddress, "recipientAddress");
        if (recipientAddress.isBlank()) {
            throw new IllegalArgumentException("받는 곳이 비어 있습니다");
        }
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    public boolean succeeded() {
        return result.isSuccess();
    }

    /** 실패 사유. 성공했으면 비어 있다. */
    public Optional<String> failureReason() {
        return result instanceof SendResult.Failure failure ? Optional.of(failure.reason()) : Optional.empty();
    }
}
