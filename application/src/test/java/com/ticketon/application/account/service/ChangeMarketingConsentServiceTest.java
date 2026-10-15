package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.ChangeMarketingConsentCommand;
import com.ticketon.application.account.port.in.GetMarketingConsentQuery;
import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("마케팅 수신 동의 변경·조회 유스케이스 (docs/12 \"마케팅 수신은 구분해서 표시\", \"마케팅 발송\")")
class ChangeMarketingConsentServiceTest {

    private static final String SEOYUN_EMAIL = "seoyun@example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final Instant AGREED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 11, 1, 9, 30));

    private static final Instant WITHDRAWN_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 11, 3, 21, 0));

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);
    private final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    private final MutableClock clock = MutableClock.at(WITHDRAWN_AT);

    private final ChangeMarketingConsentService service =
            new ChangeMarketingConsentService(accountRepository, sendNotificationUseCase, clock);
    private final GetMarketingConsentService getService = new GetMarketingConsentService(accountRepository);

    @Test
    @DisplayName("철회하면 동의 기록에 철회 줄이 새 일시로 더해지고, 가입 때 기록은 그대로 남는다")
    void withdrawalAppendsRecord() {
        long id = accounts.activeAgreeingToMarketing(SEOYUN_EMAIL, SEOYUN_PHONE);

        MarketingConsentResult result = service.changeConsent(command(id, false));

        assertThat(result).isEqualTo(new MarketingConsentResult(false, null));
        assertThat(accountRepository.findById(id).orElseThrow().consentRecords())
                .filteredOn(consentRecord -> consentRecord.item() == ConsentItem.MARKETING)
                .containsExactly(
                        new ConsentRecord(ConsentItem.MARKETING, true, StoredAudienceAccounts.SIGNUP_TIME),
                        new ConsentRecord(ConsentItem.MARKETING, false, WITHDRAWN_AT));
    }

    @Test
    @DisplayName("철회하면 처리 결과 안내를 그 관객에게 바로 보낸다 — 광고가 없고 끌 수 없는 이메일 알림이다")
    void withdrawalSendsResultNotice() {
        long id = accounts.activeAgreeingToMarketing(SEOYUN_EMAIL, SEOYUN_PHONE);

        service.changeConsent(command(id, false));

        assertThat(sendNotificationUseCase.sentCommands()).singleElement().satisfies(command -> {
            assertThat(command.recipient()).isEqualTo(NotificationRecipient.audience(id));
            assertThat(command.kind()).isEqualTo(NotificationKind.MARKETING_CONSENT_CHANGED);
            assertThat(command.content().title()).isEqualTo("마케팅 수신 동의 철회가 처리되었습니다");
            assertThat(command.content().body())
                    .contains("2026-11-03에 마케팅 수신 동의를 철회하셨습니다.")
                    .contains("마이페이지 알림 설정")
                    .doesNotContain("(광고)");
        });
    }

    @Test
    @DisplayName("기록을 더하기 전에 계정을 잠그고 읽는다 — 거의 동시에 온 두 번째 철회가 기록과 안내를 또 남기지 않게")
    void locksAccountBeforeAppending() {
        long id = accounts.activeAgreeingToMarketing(SEOYUN_EMAIL, SEOYUN_PHONE);

        service.changeConsent(command(id, false));

        assertThat(accountRepository.accountWrites()).containsExactly("잠금 AUDIENCE:" + id, "동의 기록 추가 AUDIENCE:" + id);
    }

    @Test
    @DisplayName("가입 때 동의하지 않은 관객이 동의하면 동의 줄이 더해지고, 동의 일시를 돌려주며, 동의 처리 안내를 보낸다")
    void agreementAppendsRecordAndSendsNotice() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        clock.moveTo(AGREED_AT);

        MarketingConsentResult result = service.changeConsent(command(id, true));

        assertThat(result).isEqualTo(new MarketingConsentResult(true, AGREED_AT));
        assertThat(sendNotificationUseCase.sentCommands()).singleElement().satisfies(command -> {
            assertThat(command.content().title()).isEqualTo("마케팅 수신 동의가 처리되었습니다");
            assertThat(command.content().body())
                    .contains("2026-11-01에 마케팅 수신(신규 행사 소식 이메일)에 동의하셨습니다.")
                    .contains("언제든 철회할 수 있습니다")
                    .doesNotContain("(광고)");
        });
    }

    @Test
    @DisplayName("안내의 날짜는 한국 날짜다 — 한국 시각 자정 30분은 UTC로 전날이어도 그날로 적는다")
    void noticeUsesKoreanDate() {
        long id = accounts.activeAgreeingToMarketing(SEOYUN_EMAIL, SEOYUN_PHONE);
        clock.moveTo(LocalDateTime.of(2026, 11, 4, 0, 30));

        service.changeConsent(command(id, false));

        assertThat(sendNotificationUseCase.sentCommands()).singleElement()
                .satisfies(command -> assertThat(command.content().body()).startsWith("2026-11-04에"));
    }

    @Test
    @DisplayName("지금 상태와 같으면 기록도 안내도 남기지 않고 지금 상태를 돌려준다")
    void sameStateDoesNothing() {
        long notAgreed = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        long agreed = accounts.activeAgreeingToMarketing("dohyun@example.com", "010-2222-3333");

        assertThat(service.changeConsent(command(notAgreed, false))).isEqualTo(new MarketingConsentResult(false, null));
        assertThat(service.changeConsent(command(agreed, true)))
                .isEqualTo(new MarketingConsentResult(true, StoredAudienceAccounts.SIGNUP_TIME));

        assertThat(accountRepository.accountWrites()).containsExactly("잠금 AUDIENCE:" + notAgreed, "잠금 AUDIENCE:" + agreed);
        assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("이용 정지 중에도 철회할 수 있다 — 동의는 언제든 철회할 수 있어야 한다")
    void suspendedAccountCanWithdraw() {
        long id = accounts.suspended(SEOYUN_EMAIL, SEOYUN_PHONE);
        clock.moveTo(AGREED_AT);
        service.changeConsent(command(id, true));
        clock.moveTo(WITHDRAWN_AT);

        assertThat(service.changeConsent(command(id, false)).agreed()).isFalse();
        assertThat(sendNotificationUseCase.sentCommands()).hasSize(2);
    }

    @Test
    @DisplayName("인증 대기 계정은 바꿀 수 없다 — 기록도 안내도 남지 않는다")
    void pendingAccountCannotChange() {
        long id = accounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.changeConsent(command(id, true)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("조회는 지금 상태와, 동의한 상태면 그 동의 일시를 돌려준다")
    void getsCurrentConsent() {
        long agreed = accounts.activeAgreeingToMarketing(SEOYUN_EMAIL, SEOYUN_PHONE);
        long notAgreed = accounts.active("dohyun@example.com", "010-2222-3333");

        assertThat(getService.getConsent(query(agreed)))
                .isEqualTo(new MarketingConsentResult(true, StoredAudienceAccounts.SIGNUP_TIME));
        assertThat(getService.getConsent(query(notAgreed))).isEqualTo(new MarketingConsentResult(false, null));

        service.changeConsent(command(agreed, false));

        assertThat(getService.getConsent(query(agreed))).isEqualTo(new MarketingConsentResult(false, null));
    }

    @Test
    @DisplayName("동의 상태와 동의 일시는 함께 있거나 함께 없다")
    void resultRequiresAgreedAtOnlyWhenAgreed() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new MarketingConsentResult(true, null));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new MarketingConsentResult(false, AGREED_AT));
    }

    private static ChangeMarketingConsentCommand command(long accountId, boolean agreed) {
        return new ChangeMarketingConsentCommand(AccountRef.of(AccountKind.AUDIENCE, accountId), agreed);
    }

    private static GetMarketingConsentQuery query(long accountId) {
        return new GetMarketingConsentQuery(AccountRef.of(AccountKind.AUDIENCE, accountId));
    }
}
