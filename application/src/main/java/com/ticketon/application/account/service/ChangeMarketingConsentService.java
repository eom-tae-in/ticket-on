package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.ChangeMarketingConsentCommand;
import com.ticketon.application.account.port.in.ChangeMarketingConsentUseCase;
import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.domain.account.AudienceAccount;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마케팅 수신 동의·철회 — 계정을 잠그고 읽어 바꿀 수 있는지는 계정({@link AudienceAccount#changeMarketingConsent})에 묻고, 바뀌었으면 새
 * 동의 기록을 더한 뒤 처리 결과 안내를 보낸다({@link MarketingConsentChangedNotice}).
 * <p>
 * 잠그는 까닭: 같은 관객이 거의 동시에 두 번 철회해도 뒤에 온 요청이 앞 요청의 기록을 보고 아무것도 하지 않게 해, 기록과 안내가 두 번 남지
 * 않는다({@link AudienceAccountRepository#findByIdForUpdate}).
 */
@Service
public class ChangeMarketingConsentService implements ChangeMarketingConsentUseCase {

    private final AudienceAccountRepository accountRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public ChangeMarketingConsentService(AudienceAccountRepository accountRepository,
            SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.accountRepository = accountRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MarketingConsentResult changeConsent(ChangeMarketingConsentCommand command) {
        long accountId = command.account().id();
        AudienceAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 관객 계정이 없습니다: " + accountId));
        account.changeMarketingConsent(command.agreed(), clock.instant()).ifPresent(newRecord -> {
            accountRepository.addConsentRecord(accountId, newRecord);
            sendNotificationUseCase.send(MarketingConsentChangedNotice.commandFor(accountId, newRecord));
        });
        return MarketingConsentResult.from(account);
    }
}
