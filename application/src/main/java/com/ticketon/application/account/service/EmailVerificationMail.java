package com.ticketon.application.account.service;

import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.EmailSubject;
import com.ticketon.domain.notification.RecipientSide;
import com.ticketon.domain.policy.ProposedPolicy;

/**
 * 가입 이메일 인증 메일 — 관객 쪽 알림이라 제목에 접두사가 없다(docs/12 "관객 대상", docs/01 "같은 이메일로 양쪽 가입").
 * <p>
 * 링크는 화면의 이메일 인증 페이지({@value #PAGE_PATH}?{@value #TOKEN_PARAMETER}=…)를 연다. 그 페이지가 링크 값을 인증 확인 API로 보낸다.
 */
final class EmailVerificationMail {

    /** 화면의 이메일 인증 페이지 경로. */
    static final String PAGE_PATH = "/verify-email";

    /** 링크 값을 담는 쿼리 이름. */
    static final String TOKEN_PARAMETER = "token";

    static final String TITLE = "이메일 인증을 마쳐 주세요";

    private EmailVerificationMail() {
    }

    static EmailMessage of(EmailAddress recipient, EmailVerificationToken token, WebAddress webAddress) {
        String link = webAddress.link(PAGE_PATH, TOKEN_PARAMETER, token.value());
        return new EmailMessage(recipient.value(), EmailSubject.notice(RecipientSide.AUDIENCE, TITLE), body(link));
    }

    private static String body(String link) {
        return """
                아래 링크를 열면 이메일 인증이 끝나고 로그인할 수 있습니다.
                %s

                링크는 %d시간 동안 열 수 있습니다.
                가입한 적이 없다면 이 메일을 무시해 주세요.
                """.formatted(link, ProposedPolicy.EMAIL_VERIFICATION_LINK_VALIDITY.toHours());
    }
}
