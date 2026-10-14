package com.ticketon.application.account.service;

import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.EmailSubject;
import com.ticketon.domain.notification.RecipientSide;
import com.ticketon.domain.policy.ProposedPolicy;

/**
 * 비밀번호 재설정 메일 — 관객 계정과 운영진 로그인이 같은 내용을 쓰고, 링크가 가리키는 쪽에 따라 제목과 링크가 다르다.
 * <ul>
 *   <li>관객: 관객 쪽 메일이라 제목에 접두사가 없다(docs/12 "관객 대상"). 링크는 관객 화면의 재설정 페이지({@value #AUDIENCE_PAGE_PATH}).</li>
 *   <li>운영진: 주최자 쪽 메일이라 제목에 [주최] 접두사가 붙는다(docs/12 "주최자 대상", docs/01 "같은 이메일로 양쪽 가입") — 같은
 *       이메일로 양쪽에 계정이 있으면 어느 쪽 비밀번호를 바꾸는 메일인지 제목에서 가려야 한다. 링크는 주최 화면의 재설정 페이지
 *       ({@value #STAFF_PAGE_PATH}).</li>
 * </ul>
 * 링크는 화면의 재설정 페이지(…?{@value #TOKEN_PARAMETER}=…)를 연다. 그 페이지가 새 비밀번호와 링크 값을 그쪽 재설정 API로 보낸다.
 */
final class PasswordResetMail {

    /** 관객 화면의 비밀번호 재설정 페이지 경로. */
    static final String AUDIENCE_PAGE_PATH = "/reset-password";

    /** 주최 화면(운영진 로그인)의 비밀번호 재설정 페이지 경로. 주최 화면은 /organizer 아래에 있다(web/src/app/organizer). */
    static final String STAFF_PAGE_PATH = "/organizer/reset-password";

    /** 링크 값을 담는 쿼리 이름. */
    static final String TOKEN_PARAMETER = "token";

    static final String TITLE = "비밀번호 재설정 안내";

    private PasswordResetMail() {
    }

    /**
     * 링크가 가리키는 계정의 이메일로 보낼 메일.
     *
     * @param recipient 그 계정에 저장된 이메일(가입할 때 적은 그대로)
     */
    static EmailMessage to(EmailAddress recipient, PasswordResetToken token, WebAddress webAddress) {
        AccountKind kind = token.account().kind();
        return switch (kind) {
            case AUDIENCE -> of(RecipientSide.AUDIENCE, AUDIENCE_PAGE_PATH, recipient, token, webAddress);
            case STAFF -> of(RecipientSide.ORGANIZER, STAFF_PAGE_PATH, recipient, token, webAddress);
            case ADMIN, CHECKIN -> throw new IllegalArgumentException("비밀번호 재설정 메일을 보내지 않는 계정 종류입니다: " + kind);
        };
    }

    private static EmailMessage of(RecipientSide side, String pagePath, EmailAddress recipient, PasswordResetToken token,
            WebAddress webAddress) {
        String link = webAddress.link(pagePath, TOKEN_PARAMETER, token.value());
        return new EmailMessage(recipient.value(), EmailSubject.notice(side, TITLE), body(link));
    }

    private static String body(String link) {
        return """
                아래 링크를 열어 새 비밀번호를 정해 주세요.
                %s

                링크는 %d분 동안 한 번만 쓸 수 있습니다. 비밀번호 찾기를 다시 하면 이 링크는 쓸 수 없게 됩니다.
                비밀번호 찾기를 한 적이 없다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
                """.formatted(link, ProposedPolicy.PASSWORD_RESET_LINK_VALIDITY.toMinutes());
    }
}
