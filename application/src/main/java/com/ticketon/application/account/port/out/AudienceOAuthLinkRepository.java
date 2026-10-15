package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.error.ConflictException;
import java.util.List;
import java.util.Optional;

/**
 * 관객 계정과 카카오·구글 계정의 연결 저장소 — 나가는 포트.
 * <p>
 * 제공자 계정 하나는 관객 계정 하나에만, 관객 계정 하나에는 제공자마다 하나씩만 연결된다. 규칙은 도메인
 * (AudienceLoginMethods)이 보고, 확인과 저장 사이에 다른 요청이 먼저 저장했으면 저장할 때 다시 막는다({@link #add}).
 *
 * <h2>잠금 — 연결·해제는 계정부터</h2>
 * 이미 로그인한 관객의 연결·해제는 <b>연결을 읽기 전에 {@link #lockAccount}로 그 관객 계정을 잠근다.</b> 비밀번호 없는 관객이 남은 두 연결을
 * 거의 동시에 해제하면, 잠그지 않으면 둘 다 "다른 연결이 남는다"고 보고 지워 로그인 수단이 하나도 남지 않는다. 잠그면 뒤에 온 해제는 앞 해제가
 * 끝난 뒤의 연결을 보고 막힌다. 잠금은 트랜잭션이 끝날 때 풀린다.
 */
public interface AudienceOAuthLinkRepository {

    /**
     * 그 관객 계정의 연결을 바꾸는 일을 이 트랜잭션이 끝날 때까지 혼자 하도록 계정을 잠근다. 다른 트랜잭션이 잠가 두었으면 끝날 때까지 기다린다.
     * 연결을 읽기 전에 맨 먼저 부른다(위 "잠금").
     */
    void lockAccount(long audienceAccountId);

    /** 그 제공자 계정의 연결. 없으면 비어 있다. */
    Optional<AudienceOAuthLink> findByIdentity(OAuthIdentity identity);

    /** 그 관객 계정의 연결 전부. 순서는 정하지 않는다. */
    List<AudienceOAuthLink> findByAccountId(long audienceAccountId);

    /**
     * 연결을 저장한다.
     *
     * @throws ConflictException 그 제공자 계정이 이미 연결돼 있거나({@code OAUTH_IDENTITY_ALREADY_LINKED}), 그 관객 계정에 같은
     *                           제공자의 연결이 이미 있으면({@code OAUTH_PROVIDER_ALREADY_LINKED}) — 거의 동시에 온 다른 요청이 먼저 저장한 경우
     */
    void add(AudienceOAuthLink link);

    /**
     * 연결을 지운다.
     *
     * @throws ConflictException 그 연결이 없으면({@code OAUTH_PROVIDER_NOT_LINKED}) — 그 사이 다른 요청이 먼저 지운 경우
     */
    void remove(AudienceOAuthLink link);
}
