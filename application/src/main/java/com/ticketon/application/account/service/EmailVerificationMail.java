package com.ticketon.application.account.service;

import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.EmailSubject;
import com.ticketon.domain.notification.RecipientSide;
import com.ticketon.domain.policy.ProposedPolicy;

/**
 * 가입 이메일 인증 메일 — 관객 계정과 운영진 로그인이 같은 내용을 쓰고, 받는 쪽에 따라 제목과 링크가 다르다.
 * <ul>
 *   <li>관객: 관객 쪽 알림이라 제목에 접두사가 없다(docs/12 "관객 대상"). 링크는 관객 화면의 인증 페이지({@value #AUDIENCE_PAGE_PATH}).</li>
 *   <li>운영진: 주최자 쪽 메일이라 제목에 [주최] 접두사가 붙는다(docs/12 "주최자 대상", docs/01 "같은 이메일로 양쪽 가입") — 같은
 *       이메일로 양쪽에 가입하면 같은 메일함에 두 메일이 오기 때문이다. 링크는 주최 화면의 인증 페이지({@value #STAFF_PAGE_PATH}).</li>
 * </ul>
 * 링크는 화면의 이메일 인증 페이지(…?{@value #TOKEN_PARAMETER}=…)를 연다. 그 페이지가 링크 값을 그쪽 인증 확인 API로 보낸다.
 */
final class EmailVerificationMail {

    /** 관객 화면의 이메일 인증 페이지 경로. */
    static final String AUDIENCE_PAGE_PATH = "/verify-email";

    /** 주최 화면(운영진 로그인)의 이메일 인증 페이지 경로. 주최 화면은 /organizer 아래에 있다(web/src/app/organizer). */
    static final String STAFF_PAGE_PATH = "/organizer/verify-email";

    /** 링크 값을 담는 쿼리 이름. */
    static final String TOKEN_PARAMETER = "token";

    static final String TITLE = "이메일 인증을 마쳐 주세요";

    private EmailVerificationMail() {
    }

    /** 관객 계정 가입 인증 메일 — 접두사 없는 제목. */
    static EmailMessage toAudience(EmailAddress recipient, EmailVerificationToken token, WebAddress webAddress) {
        return of(RecipientSide.AUDIENCE, AUDIENCE_PAGE_PATH, recipient, token.value(), webAddress);
    }

    /** 운영진 로그인 가입 인증 메일 — [주최] 접두사가 붙은 제목. */
    static EmailMessage toStaff(EmailAddress recipient, StaffEmailVerificationToken token, WebAddress webAddress) {
        return of(RecipientSide.ORGANIZER, STAFF_PAGE_PATH, recipient, token.value(), webAddress);
    }

    private static EmailMessage of(RecipientSide side, String pagePath, EmailAddress recipient, String tokenValue,
            WebAddress webAddress) {
        String link = webAddress.link(pagePath, TOKEN_PARAMETER, tokenValue);
        return new EmailMessage(recipient.value(), EmailSubject.notice(side, TITLE), body(link));
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
