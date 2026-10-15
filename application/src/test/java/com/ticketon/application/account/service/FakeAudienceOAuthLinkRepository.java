package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.error.ConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 카카오·구글 연결 저장소 — 표처럼 지킨다: 제공자 계정 하나에 관객 계정 하나, 관객 계정 하나에 제공자마다 하나, 없는 연결 지우기는 충돌.
 * 계정 잠금·연결 저장·지우기를 쓰기 기록에 남겨 잠금 순서(계정 먼저)를 볼 수 있다.
 */
final class FakeAudienceOAuthLinkRepository implements AudienceOAuthLinkRepository {

    private final Map<OAuthIdentity, AudienceOAuthLink> links = new LinkedHashMap<>();
    private final WriteLog writeLog = new WriteLog();
    private AudienceOAuthLink linkWinningNextAdd;

    /** 다음 저장 직전에 다른 요청이 그 연결을 먼저 저장한 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherRequestWinNextAdd(AudienceOAuthLink winningLink) {
        linkWinningNextAdd = winningLink;
    }

    List<AudienceOAuthLink> savedLinks() {
        return List.copyOf(links.values());
    }

    WriteLog writeLog() {
        return writeLog;
    }

    @Override
    public void lockAccount(long audienceAccountId) {
        writeLog.record("계정 잠금 AUDIENCE:" + audienceAccountId);
    }

    @Override
    public Optional<AudienceOAuthLink> findByIdentity(OAuthIdentity identity) {
        return Optional.ofNullable(links.get(identity));
    }

    @Override
    public List<AudienceOAuthLink> findByAccountId(long audienceAccountId) {
        return links.values().stream().filter(link -> link.audienceAccountId() == audienceAccountId).toList();
    }

    @Override
    public void add(AudienceOAuthLink link) {
        if (linkWinningNextAdd != null) {
            links.put(linkWinningNextAdd.identity(), linkWinningNextAdd);
            linkWinningNextAdd = null;
        }
        if (links.containsKey(link.identity())) {
            throw new ConflictException(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED, "동시 연결 경합(흉내)");
        }
        boolean providerTaken = links.values().stream().anyMatch(existing ->
                existing.audienceAccountId() == link.audienceAccountId() && existing.provider() == link.provider());
        if (providerTaken) {
            throw new ConflictException(AccountErrorCode.OAUTH_PROVIDER_ALREADY_LINKED, "동시 연결 경합(흉내)");
        }
        writeLog.record("연결 저장 AUDIENCE:" + link.audienceAccountId() + " " + link.provider());
        links.put(link.identity(), link);
    }

    @Override
    public void remove(AudienceOAuthLink link) {
        if (!link.equals(links.get(link.identity()))) {
            throw new ConflictException(AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED, "이미 지워진 연결(흉내)");
        }
        writeLog.record("연결 지우기 AUDIENCE:" + link.audienceAccountId() + " " + link.provider());
        links.remove(link.identity());
    }
}
