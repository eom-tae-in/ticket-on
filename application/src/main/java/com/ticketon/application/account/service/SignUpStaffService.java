package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.SignUpStaffCommand;
import com.ticketon.application.account.port.in.SignUpStaffResult;
import com.ticketon.application.account.port.in.SignUpStaffUseCase;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.account.port.out.StaffEmailVerificationTokenRepository;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.common.TokenGenerator;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.notification.SendResult;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 로그인 가입 — 가입 규칙은 {@link StaffAccount#signUp}이 보고, 여기서는 저장소에 이메일·닉네임을 확인해 넘기고, 저장하고,
 * 인증 링크를 만들어 [주최] 메일로 보낸다.
 * <p>
 * <b>인증 메일을 보내지 못하면 가입을 되돌린다</b> — 관객 가입과 같은 이유다. 메일이 없으면 인증할 수 없고, 인증 대기 계정이 이메일과
 * 닉네임을 쥐고 있어 다시 가입할 수도 없게 된다. 메일은 저장을 모두 마친 뒤 마지막에 보낸다 — 앞에서 막히면 메일이 나가지 않는다.
 */
@Service
public class SignUpStaffService implements SignUpStaffUseCase {

    private final StaffAccountRepository accountRepository;
    private final StaffEmailVerificationTokenRepository tokenRepository;
    private final PasswordHashPort passwordHashPort;
    private final EmailSendPort emailSendPort;
    private final TokenGenerator tokenGenerator;
    private final WebAddress webAddress;
    private final Clock clock;

    public SignUpStaffService(StaffAccountRepository accountRepository,
            StaffEmailVerificationTokenRepository tokenRepository, PasswordHashPort passwordHashPort,
            EmailSendPort emailSendPort, TokenGenerator tokenGenerator, WebAddress webAddress, Clock clock) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordHashPort = passwordHashPort;
        this.emailSendPort = emailSendPort;
        this.tokenGenerator = tokenGenerator;
        this.webAddress = webAddress;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SignUpStaffResult signUp(SignUpStaffCommand command) {
        Instant now = clock.instant();
        StaffPersonalInformation personalInformation = command.personalInformation();
        StaffAccount newAccount = StaffAccount.signUp(personalInformation, command.nickname(),
                passwordHashPort.hash(command.password()), accountRepository.emailAvailability(personalInformation.email()),
                accountRepository.nicknameAvailability(command.nickname()), now);

        long accountId = accountRepository.add(newAccount).id()
                .orElseThrow(() -> new IllegalStateException("저장한 운영진 계정에 id가 없습니다"));
        StaffEmailVerificationToken token = StaffEmailVerificationToken.issue(
                tokenGenerator.urlSafe(EmailVerificationToken.LENGTH), accountId, now);
        tokenRepository.add(token);
        sendVerificationMail(personalInformation, token);
        return new SignUpStaffResult(accountId, personalInformation.email().value());
    }

    private void sendVerificationMail(StaffPersonalInformation personalInformation, StaffEmailVerificationToken token) {
        SendResult result = emailSendPort.send(EmailVerificationMail.toStaff(personalInformation.email(), token, webAddress));
        if (result instanceof SendResult.Failure failure) {
            throw new IllegalStateException("이메일 인증 메일을 보내지 못해 운영진 가입을 되돌립니다 (계정 "
                    + token.staffAccountId() + "): " + failure.reason());
        }
    }
}
