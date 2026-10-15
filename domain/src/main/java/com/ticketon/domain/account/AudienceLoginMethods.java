package com.ticketon.domain.account;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 관객 계정 하나의 로그인 수단 — 비밀번호(정했으면)와 연결한 카카오·구글 계정.
 * <p>
 * 연결·해제는 이미 로그인한 관객이 직접 한다(docs/01 "계정 연동 — 연동은 이미 로그인한 상태에서 사용자가 직접", docs/13 "계정 정보 · OAuth 연동").
 * 이용 정지 중에도 된다 — 정지로 막히는 것은 새 예매·변경·신고와 전화번호 변경뿐이다({@link AudienceAction#MANAGE_LOGIN_METHODS}).
 * <ul>
 *   <li>제공자 계정 하나는 관객 계정 하나에만 연결된다. 다른 관객 계정에 연결된 제공자 계정은 연결하지 않는다.</li>
 *   <li>관객 계정 하나에는 제공자마다 하나씩만 연결된다. 같은 제공자의 다른 계정으로 바꾸려면 해제한 뒤 다시 연결한다.</li>
 *   <li><b>로그인 수단이 하나도 남지 않게 하는 해제는 막는다.</b> 비밀번호 없이 OAuth로만 가입한 관객이 하나뿐인 연결을 끊으면 다시
 *       들어올 길이 없다. 비밀번호는 비밀번호 찾기로 정할 수 있으므로({@link AudienceAccount#resetPassword}) 먼저 비밀번호를 정하거나
 *       다른 계정을 연결한 뒤 해제한다.</li>
 * </ul>
 * 연결·해제의 결과(저장하거나 지울 연결)를 돌려주고, 이 객체도 그 결과를 반영한다.
 */
public final class AudienceLoginMethods {

    private final long audienceAccountId;
    private final boolean passwordSet;
    private final boolean manageable;
    private final Map<OAuthProvider, AudienceOAuthLink> links;

    private AudienceLoginMethods(long audienceAccountId, boolean passwordSet, boolean manageable,
            Map<OAuthProvider, AudienceOAuthLink> links) {
        this.audienceAccountId = audienceAccountId;
        this.passwordSet = passwordSet;
        this.manageable = manageable;
        this.links = links;
    }

    /**
     * 계정과 그 계정의 연결로 만든다.
     *
     * @param account 저장된 관객 계정
     * @param links   그 계정에 연결된 제공자 계정 전부 — 저장소가 찾은 값
     * @throws IllegalArgumentException 저장하지 않은 계정이거나, 다른 계정의 연결이 섞였거나, 같은 제공자의 연결이 둘 이상이면
     */
    public static AudienceLoginMethods of(AudienceAccount account, List<AudienceOAuthLink> links) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(links, "links");
        long accountId = account.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        Map<OAuthProvider, AudienceOAuthLink> linksByProvider = new EnumMap<>(OAuthProvider.class);
        for (AudienceOAuthLink link : links) {
            if (link.audienceAccountId() != accountId) {
                throw new IllegalArgumentException("계정 " + accountId + "의 연결이 아닙니다: " + link);
            }
            if (linksByProvider.putIfAbsent(link.provider(), link) != null) {
                throw new IllegalArgumentException("계정 " + accountId + "에 같은 제공자의 연결이 둘 이상입니다: " + link.provider());
            }
        }
        return new AudienceLoginMethods(accountId, account.passwordHash().isPresent(),
                account.can(AudienceAction.MANAGE_LOGIN_METHODS), linksByProvider);
    }

    /**
     * 제공자 계정을 이 관객 계정에 연결한다.
     *
     * @param identity        연결할 제공자 계정 — 관객이 방금 그 제공자에서 로그인해 확인한 계정
     * @param linkOfIdentity  그 제공자 계정에 이미 있는 연결 — 저장소가 찾은 값. 없으면 비어 있다.
     * @param now             연결 일시
     * @return 새로 저장할 연결. 이미 이 계정에 연결된 제공자 계정이면 비어 있다(할 일이 없다).
     * @throws RuleViolationException 로그인 수단을 바꿀 수 없는 상태(인증 대기·탈퇴 — {@link AccountErrorCode#AUDIENCE_NOT_ACTIVE}),
     *                                다른 관객 계정에 연결된 제공자 계정({@link AccountErrorCode#OAUTH_IDENTITY_ALREADY_LINKED}),
     *                                같은 제공자의 다른 계정이 이미 연결돼 있으면({@link AccountErrorCode#OAUTH_PROVIDER_ALREADY_LINKED})
     */
    public Optional<AudienceOAuthLink> link(OAuthIdentity identity, Optional<AudienceOAuthLink> linkOfIdentity,
            Instant now) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(linkOfIdentity, "linkOfIdentity");
        Objects.requireNonNull(now, "now");
        requireManageable();
        if (linkOfIdentity.isPresent()) {
            AudienceOAuthLink existingLink = linkOfIdentity.get();
            if (!existingLink.identity().equals(identity)) {
                throw new IllegalArgumentException("그 제공자 계정의 연결이 아닙니다: " + existingLink);
            }
            if (existingLink.audienceAccountId() != audienceAccountId) {
                throw new RuleViolationException(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED,
                        identity.provider() + " 계정이 관객 계정 " + existingLink.audienceAccountId() + "에 연결돼 있음");
            }
            return Optional.empty();
        }
        if (links.containsKey(identity.provider())) {
            throw new RuleViolationException(AccountErrorCode.OAUTH_PROVIDER_ALREADY_LINKED,
                    "계정 " + audienceAccountId + "에 다른 " + identity.provider() + " 계정이 연결돼 있음");
        }
        AudienceOAuthLink newLink = new AudienceOAuthLink(audienceAccountId, identity, now);
        links.put(identity.provider(), newLink);
        return Optional.of(newLink);
    }

    /**
     * 그 제공자의 연결을 해제한다.
     *
     * @return 지울 연결
     * @throws RuleViolationException 로그인 수단을 바꿀 수 없는 상태(인증 대기·탈퇴 — {@link AccountErrorCode#AUDIENCE_NOT_ACTIVE}),
     *                                그 제공자의 연결이 없거나({@link AccountErrorCode#OAUTH_PROVIDER_NOT_LINKED}),
     *                                해제하면 로그인 수단이 하나도 남지 않으면({@link AccountErrorCode#LAST_LOGIN_METHOD})
     */
    public AudienceOAuthLink unlink(OAuthProvider provider) {
        Objects.requireNonNull(provider, "provider");
        requireManageable();
        AudienceOAuthLink link = links.get(provider);
        if (link == null) {
            throw new RuleViolationException(AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED,
                    "계정 " + audienceAccountId + "에 " + provider + " 연결 없음");
        }
        if (!passwordSet && links.size() == 1) {
            throw new RuleViolationException(AccountErrorCode.LAST_LOGIN_METHOD,
                    "계정 " + audienceAccountId + "은 비밀번호가 없고 " + provider + " 연결 하나뿐");
        }
        links.remove(provider);
        return link;
    }

    /** 연결된 제공자 — 제공자 선언 순서(카카오, 구글)대로. */
    public List<OAuthProvider> linkedProviders() {
        return List.copyOf(links.keySet());
    }

    /** 비밀번호를 정했는지. OAuth로만 가입하고 비밀번호 찾기로 비밀번호를 정하지 않았으면 아니다. */
    public boolean isPasswordSet() {
        return passwordSet;
    }

    private void requireManageable() {
        if (!manageable) {
            throw new RuleViolationException(AccountErrorCode.AUDIENCE_NOT_ACTIVE,
                    "계정 " + audienceAccountId + " 로그인 수단을 바꿀 수 없는 상태");
        }
    }
}
