package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.OAuthProvider;
import java.util.List;
import java.util.Objects;

/**
 * 내 관객 계정 — 마이페이지 "계정 정보"(docs/13)와 화면이 지금 누가 로그인했는지 알 때 쓴다.
 *
 * @param name                 이름
 * @param email                이메일(가입할 때 적은 그대로)
 * @param phoneNumber          전화번호 — 숫자만 남긴 11자리
 * @param suspended            이용 정지 중인지. 정지 중에는 새 예매·변경·신고·전화번호 변경이 막힌다(docs/01 "관객 이용 정지").
 * @param linkedOAuthProviders 연결된 카카오·구글 — 제공자 선언 순서(카카오, 구글)대로
 */
public record GetMyAudienceAccountResult(String name, String email, String phoneNumber, boolean suspended,
        List<OAuthProvider> linkedOAuthProviders) {

    public GetMyAudienceAccountResult {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phoneNumber, "phoneNumber");
        linkedOAuthProviders = List.copyOf(linkedOAuthProviders);
    }
}
