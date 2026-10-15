package com.ticketon.application.account.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 마케팅 수신 동의·철회 처리 결과 안내 — 동의나 철회가 처리되면 그 관객에게 바로 이메일로 보낸다. 법이 처리 결과를 14일 안에 알리라고
 * 하고, 이 안내에는 광고를 넣지 않는다(docs/12 "마케팅 발송", docs/99 A-6). 거래 알림처럼 보내므로 제목에 (광고)가 붙지 않는다.
 * <p>
 * 본문에는 무엇을 언제(한국 날짜) 처리했는지와, 다시 바꾸는 방법(마이페이지 알림 설정)을 적는다.
 */
final class MarketingConsentChangedNotice {

    static final String AGREED_TITLE = "마케팅 수신 동의가 처리되었습니다";

    static final String WITHDRAWN_TITLE = "마케팅 수신 동의 철회가 처리되었습니다";

    private MarketingConsentChangedNotice() {
    }

    /**
     * @param marketingConsent 방금 더한 마케팅 수신 동의 기록
     */
    static SendNotificationCommand commandFor(long audienceAccountId, ConsentRecord marketingConsent) {
        Objects.requireNonNull(marketingConsent, "marketingConsent");
        if (marketingConsent.item() != ConsentItem.MARKETING) {
            throw new IllegalArgumentException("마케팅 수신 동의 기록이 아닙니다: " + marketingConsent.item());
        }
        LocalDate decidedOn = KoreanTime.toDate(marketingConsent.recordedAt());
        NotificationContent content = marketingConsent.agreed()
                ? new NotificationContent(AGREED_TITLE, agreedBody(decidedOn))
                : new NotificationContent(WITHDRAWN_TITLE, withdrawnBody(decidedOn));
        return new SendNotificationCommand(NotificationRecipient.audience(audienceAccountId),
                NotificationKind.MARKETING_CONSENT_CHANGED, content);
    }

    private static String agreedBody(LocalDate decidedOn) {
        return """
                %s에 마케팅 수신(신규 행사 소식 이메일)에 동의하셨습니다.
                앞으로 신규 행사 소식을 이메일로 보내 드립니다.
                받지 않으려면 마이페이지 알림 설정에서 언제든 철회할 수 있습니다.
                """.formatted(decidedOn);
    }

    private static String withdrawnBody(LocalDate decidedOn) {
        return """
                %s에 마케팅 수신 동의를 철회하셨습니다.
                앞으로 신규 행사 소식 이메일을 보내지 않습니다. 예매·취소 확인 같은 알림은 알림 설정대로 받습니다.
                다시 받으려면 마이페이지 알림 설정에서 동의할 수 있습니다.
                """.formatted(decidedOn);
    }
}
