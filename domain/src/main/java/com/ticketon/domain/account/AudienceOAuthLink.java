package com.ticketon.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * 관객 계정과 제공자 계정의 연결 한 줄 — 이 연결이 있으면 그 제공자 계정으로 로그인했을 때 이 관객 계정으로 들어온다.
 * <p>
 * 연결은 OAuth로 가입할 때 생기거나, 이미 로그인한 관객이 직접 만든다({@link AudienceLoginMethods#link}).
 * <b>이메일이 같다고 저절로 묶지 않는다</b> — 공격자가 피해자의 이메일로 OAuth 가입을 시도하면 기존 계정에 들어가게 된다(docs/01 "계정 연동").
 * 제공자 계정 하나는 관객 계정 하나에만, 관객 계정 하나에는 제공자마다 하나씩만 연결된다.
 *
 * @param audienceAccountId 관객 계정 id (1 이상)
 * @param identity          연결한 제공자 계정
 * @param linkedAt          연결한 일시
 */
public record AudienceOAuthLink(long audienceAccountId, OAuthIdentity identity, Instant linkedAt) {

    public AudienceOAuthLink {
        if (audienceAccountId < 1) {
            throw new IllegalArgumentException("관객 계정 id는 1 이상이어야 합니다: " + audienceAccountId);
        }
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }

    public OAuthProvider provider() {
        return identity.provider();
    }
}
