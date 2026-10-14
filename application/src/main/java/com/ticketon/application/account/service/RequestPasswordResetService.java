package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.RequestPasswordResetCommand;
import com.ticketon.application.account.port.in.RequestPasswordResetUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.PasswordResetTokenRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.common.TokenGenerator;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAction;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.notification.SendResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 찾기 — 고른 쪽 저장소에서 이메일로 가입한 계정을 찾고, 비밀번호 찾기를 할 수 있는지는 계정에 묻는다
 * ({@link AudienceAccount#can}, {@link StaffAccount#canResetPassword}). 할 수 있으면 이전 링크를 대체하고 새 링크를 저장한 뒤 그 계정의
 * 이메일로 보낸다. 없거나 할 수 없으면(인증 대기·탈퇴) 아무것도 하지 않는다.
 * <p>
 * 고른 쪽 계정만 본다. 관객 쪽을 고르면 같은 이메일의 운영진 로그인이 있어도 보지 않고, 그 반대도 그렇다.
 * <p>
 * <b>메일을 보내지 못하면 새 링크를 되돌린다</b> — 쓸 수 없는 링크가 이전 링크를 대체한 채 남지 않게. 메일은 저장을 모두 마친 뒤
 * 마지막에 보낸다. 부른 쪽에 결과를 알리지 않는 까닭과 응답과 떼어 부르는 까닭은 {@link RequestPasswordResetUseCase}에 있다.
 */
@Service
public class RequestPasswordResetService implements RequestPasswordResetUseCase {

    private final AudienceAccountRepository audienceAccountRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailSendPort emailSendPort;
    private final TokenGenerator tokenGenerator;
    private final WebAddress webAddress;
    private final Clock clock;

    public RequestPasswordResetService(AudienceAccountRepository audienceAccountRepository,
            StaffAccountRepository staffAccountRepository, PasswordResetTokenRepository tokenRepository,
            EmailSendPort emailSendPort, TokenGenerator tokenGenerator, WebAddress webAddress, Clock clock) {
        this.audienceAccountRepository = audienceAccountRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.tokenRepository = tokenRepository;
        this.emailSendPort = emailSendPort;
        this.tokenGenerator = tokenGenerator;
        this.webAddress = webAddress;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void requestReset(RequestPasswordResetCommand command) {
        Instant now = clock.instant();
        resettableAccount(command.accountKind(), command.email())
                .ifPresent(recipient -> issueAndSend(recipient, now));
    }

    /** 고른 쪽에서 그 이메일로 가입한, 비밀번호 찾기를 할 수 있는 계정과 그 계정에 저장된 이메일. */
    private Optional<ResetMailRecipient> resettableAccount(AccountKind accountKind, EmailAddress email) {
        return switch (accountKind) {
            case AUDIENCE -> audienceAccountRepository.findRegisteredByEmail(email)
                    .filter(account -> account.can(AudienceAction.RESET_PASSWORD))
                    .map(account -> new ResetMailRecipient(AccountRef.of(AccountKind.AUDIENCE, idOf(account.id())),
                            account.personalInformation().email()));
            case STAFF -> staffAccountRepository.findRegisteredByEmail(email)
                    .filter(StaffAccount::canResetPassword)
                    .map(account -> new ResetMailRecipient(AccountRef.of(AccountKind.STAFF, idOf(account.id())),
                            account.personalInformation().email()));
            case ADMIN, CHECKIN -> throw new IllegalArgumentException("비밀번호 찾기를 하지 않는 계정 종류입니다: " + accountKind);
        };
    }

    /** 잠금 순서는 계정 → 링크다(PasswordResetTokenRepository "잠금 순서") — 재설정과 같은 순서라 서로 교착되지 않는다. */
    private void issueAndSend(ResetMailRecipient recipient, Instant now) {
        tokenRepository.lockAccount(recipient.account());
        tokenRepository.replaceUnusedTokensOf(recipient.account(), now);
        PasswordResetToken token = PasswordResetToken.issue(tokenGenerator.urlSafe(PasswordResetToken.LENGTH),
                recipient.account(), now);
        tokenRepository.add(token);
        SendResult result = emailSendPort.send(PasswordResetMail.to(recipient.email(), token, webAddress));
        if (result instanceof SendResult.Failure failure) {
            throw new IllegalStateException("비밀번호 재설정 메일을 보내지 못해 새 링크를 되돌립니다 ("
                    + recipient.account().principalName() + "): " + failure.reason());
        }
    }

    private static long idOf(OptionalLong id) {
        return id.orElseThrow(() -> new IllegalStateException("저장소에서 읽은 계정에 id가 없습니다"));
    }

    /** 재설정 메일을 받을 계정과 그 계정에 저장된 이메일. */
    private record ResetMailRecipient(AccountRef account, EmailAddress email) {
    }
}
