package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_OAUTH_LINK;

import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.persistence.jooq.tables.records.AudienceOauthLinkRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 관객 계정과 카카오·구글 계정의 연결 저장소(audience_oauth_link — V7) 어댑터.
 *
 * <h2>하나에 하나 — 동시 연결 경합</h2>
 * 제공자 계정 하나에 관객 계정 하나(기본 키), 관객 계정 하나에 제공자마다 하나(유일 제약)는 표가 마지막으로 판정한다.
 * 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지 않으면 겹친 쪽을 다시 조회해 그 코드의 충돌(409)로 알린다.
 * 예외로 받지 않는 이유는 관객 계정 저장소와 같다 — PostgreSQL은 문장이 실패하면 그 트랜잭션의 다음 조회를 받지 않는다.
 *
 * <h2>잠금</h2>
 * {@link #lockAccount}는 그 관객 계정 줄(audience_account)을 {@code SELECT … FOR UPDATE}로 잠근다. 비밀번호 찾기(계정 잠금 → 링크)와 같은 줄을
 * 같은 순서로 맨 먼저 잠그므로 서로 교착되지 않는다.
 */
@Component
public class AudienceOAuthLinkPersistenceAdapter implements AudienceOAuthLinkRepository {

    private final DSLContext dsl;

    public AudienceOAuthLinkPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void lockAccount(long audienceAccountId) {
        dsl.select(AUDIENCE_ACCOUNT.ID)
                .from(AUDIENCE_ACCOUNT)
                .where(AUDIENCE_ACCOUNT.ID.eq(audienceAccountId))
                .forUpdate()
                .execute();
    }

    @Override
    public Optional<AudienceOAuthLink> findByIdentity(OAuthIdentity identity) {
        return dsl.selectFrom(AUDIENCE_OAUTH_LINK)
                .where(sameIdentity(identity))
                .fetchOptional()
                .map(this::toLink);
    }

    @Override
    public List<AudienceOAuthLink> findByAccountId(long audienceAccountId) {
        return dsl.selectFrom(AUDIENCE_OAUTH_LINK)
                .where(AUDIENCE_OAUTH_LINK.AUDIENCE_ACCOUNT_ID.eq(audienceAccountId))
                .orderBy(AUDIENCE_OAUTH_LINK.LINKED_AT, AUDIENCE_OAUTH_LINK.PROVIDER)
                .fetch(this::toLink);
    }

    @Override
    public void add(AudienceOAuthLink link) {
        int insertedRows = dsl.insertInto(AUDIENCE_OAUTH_LINK)
                .set(AUDIENCE_OAUTH_LINK.PROVIDER, link.provider().name())
                .set(AUDIENCE_OAUTH_LINK.PROVIDER_USER_ID, link.identity().providerUserId())
                .set(AUDIENCE_OAUTH_LINK.AUDIENCE_ACCOUNT_ID, link.audienceAccountId())
                .set(AUDIENCE_OAUTH_LINK.LINKED_AT, toDatabaseTime(link.linkedAt()))
                .onConflictDoNothing()
                .execute();
        if (insertedRows == 0) {
            throw lostLinkRace(link);
        }
    }

    @Override
    public void remove(AudienceOAuthLink link) {
        int deletedRows = dsl.deleteFrom(AUDIENCE_OAUTH_LINK)
                .where(sameIdentity(link.identity()))
                .and(AUDIENCE_OAUTH_LINK.AUDIENCE_ACCOUNT_ID.eq(link.audienceAccountId()))
                .execute();
        if (deletedRows == 0) {
            throw new ConflictException(AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED,
                    "이미 지워진 연결: 계정 " + link.audienceAccountId() + " " + link.provider());
        }
    }

    /** 표의 제약에 막혀 저장하지 못했을 때 — 겹친 쪽을 다시 보고 그 코드로 알린다. 제공자 계정을 먼저 본다. */
    private RuntimeException lostLinkRace(AudienceOAuthLink link) {
        if (dsl.fetchExists(AUDIENCE_OAUTH_LINK, sameIdentity(link.identity()))) {
            return new ConflictException(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED,
                    "동시 연결 경합 — " + link.provider() + " 계정이 먼저 연결됨");
        }
        if (dsl.fetchExists(AUDIENCE_OAUTH_LINK, AUDIENCE_OAUTH_LINK.AUDIENCE_ACCOUNT_ID.eq(link.audienceAccountId())
                .and(AUDIENCE_OAUTH_LINK.PROVIDER.eq(link.provider().name())))) {
            return new ConflictException(AccountErrorCode.OAUTH_PROVIDER_ALREADY_LINKED,
                    "동시 연결 경합 — 계정 " + link.audienceAccountId() + "에 " + link.provider() + " 연결이 먼저 저장됨");
        }
        return new IllegalStateException("연결을 저장하지 못했는데 겹치는 연결이 없습니다: " + link);
    }

    private static Condition sameIdentity(OAuthIdentity identity) {
        return AUDIENCE_OAUTH_LINK.PROVIDER.eq(identity.provider().name())
                .and(AUDIENCE_OAUTH_LINK.PROVIDER_USER_ID.eq(identity.providerUserId()));
    }

    private AudienceOAuthLink toLink(AudienceOauthLinkRecord link) {
        return new AudienceOAuthLink(link.getAudienceAccountId(),
                new OAuthIdentity(OAuthProvider.valueOf(link.getProvider()), link.getProviderUserId()),
                link.getLinkedAt().toInstant());
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
