package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.PASSWORD_RESET_TOKEN;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;

import com.ticketon.application.account.port.out.PasswordResetTokenRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.persistence.jooq.tables.records.PasswordResetTokenRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.TableField;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 링크 저장소(password_reset_token — V6) 어댑터.
 * <p>
 * 링크 한 줄은 관객 계정(audience_account_id)이나 운영진 로그인(staff_account_id) 중 꼭 하나를 가리킨다. 어느 칸이 채워졌는지가
 * 계정 종류다({@link AccountRef#kind()}).
 *
 * <h2>잠금 순서 — 계정 → 링크</h2>
 * {@link #lockAccount}는 그 계정 줄(audience_account·staff_account)을 {@code SELECT … FOR UPDATE}로 잠근다. 비밀번호 찾기와 재설정은
 * 둘 다 링크나 계정 줄을 바꾸기 전에 이것을 맨 먼저 부른다(포트의 "잠금 순서"). 그래서
 * <ul>
 *   <li>새 링크는 "이전 링크 대체 → 저장" 두 단계인데, 같은 계정의 비밀번호 찾기 둘이 서로의 새 링크를 보지 못한 채 둘 다 저장되는 일이
 *       없다 — 뒤에 온 요청은 앞 요청이 끝날 때까지 기다렸다가 앞 요청이 저장한 링크까지 대체한다. 표의 부분 유일 색인(계정마다 쓰지도
 *       대체되지도 않은 줄 하나)이 마지막 장치다.</li>
 *   <li>찾기(링크 줄을 바꿈)와 재설정(링크 줄과 계정 줄을 바꿈)이 서로 반대 순서로 줄을 잠가 교착되지 않는다.</li>
 * </ul>
 *
 * <h2>1회용 — 동시 사용</h2>
 * 같은 링크로 거의 동시에 비밀번호를 바꾸면 {@link #saveUse}의 조건부 갱신({@code WHERE used_at IS NULL AND replaced_at IS NULL})이
 * 하나만 통과시킨다. 진 쪽은 충돌(409)이 되고 트랜잭션이 되돌려져 비밀번호도 바뀌지 않는다.
 */
@Component
public class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenRepository {

    private final DSLContext dsl;

    public PasswordResetTokenPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 그 계정 줄을 트랜잭션이 끝날 때까지 잠근다. 다른 트랜잭션이 잠가 두었으면 끝날 때까지 기다린다. */
    @Override
    public void lockAccount(AccountRef account) {
        switch (account.kind()) {
            case AUDIENCE -> dsl.select(AUDIENCE_ACCOUNT.ID)
                    .from(AUDIENCE_ACCOUNT)
                    .where(AUDIENCE_ACCOUNT.ID.eq(account.id()))
                    .forUpdate()
                    .execute();
            case STAFF -> dsl.select(STAFF_ACCOUNT.ID)
                    .from(STAFF_ACCOUNT)
                    .where(STAFF_ACCOUNT.ID.eq(account.id()))
                    .forUpdate()
                    .execute();
            case ADMIN, CHECKIN -> throw notResettable(account.kind());
        }
    }

    @Override
    public int replaceUnusedTokensOf(AccountRef account, Instant replacedAt) {
        return dsl.update(PASSWORD_RESET_TOKEN)
                .set(PASSWORD_RESET_TOKEN.REPLACED_AT, toDatabaseTime(replacedAt))
                .where(accountColumn(account.kind()).eq(account.id()))
                .and(unused())
                .execute();
    }

    @Override
    public void add(PasswordResetToken token) {
        AccountRef account = token.account();
        dsl.insertInto(PASSWORD_RESET_TOKEN)
                .set(PASSWORD_RESET_TOKEN.TOKEN, token.value())
                .set(accountColumn(account.kind()), account.id())
                .set(PASSWORD_RESET_TOKEN.ISSUED_AT, toDatabaseTime(token.issuedAt()))
                .set(PASSWORD_RESET_TOKEN.EXPIRES_AT, toDatabaseTime(token.expiresAt()))
                .set(PASSWORD_RESET_TOKEN.USED_AT, token.usedAt().map(this::toDatabaseTime).orElse(null))
                .set(PASSWORD_RESET_TOKEN.REPLACED_AT, token.replacedAt().map(this::toDatabaseTime).orElse(null))
                .execute();
    }

    @Override
    public Optional<PasswordResetToken> findByValue(AccountKind accountKind, String value) {
        return dsl.selectFrom(PASSWORD_RESET_TOKEN)
                .where(PASSWORD_RESET_TOKEN.TOKEN.eq(value))
                .and(accountColumn(accountKind).isNotNull())
                .fetchOptional()
                .map(this::toToken);
    }

    @Override
    public void saveUse(PasswordResetToken usedToken) {
        Instant usedAt = usedToken.usedAt()
                .orElseThrow(() -> new IllegalArgumentException("비밀번호를 바꾸지 않은 링크입니다: " + usedToken));
        int updatedRows = dsl.update(PASSWORD_RESET_TOKEN)
                .set(PASSWORD_RESET_TOKEN.USED_AT, toDatabaseTime(usedAt))
                .where(PASSWORD_RESET_TOKEN.TOKEN.eq(usedToken.value()))
                .and(unused())
                .execute();
        if (updatedRows == 0) {
            throw lostUseRace(usedToken);
        }
    }

    /** 그 사이 링크가 쓰였는지 대체됐는지 다시 보고 그쪽 문구로 알린다. */
    private ConflictException lostUseRace(PasswordResetToken usedToken) {
        boolean replaced = dsl.fetchExists(PASSWORD_RESET_TOKEN,
                PASSWORD_RESET_TOKEN.TOKEN.eq(usedToken.value()).and(PASSWORD_RESET_TOKEN.REPLACED_AT.isNotNull()));
        String reasonForLog = "재설정 링크 사용 경합 — " + usedToken.account().principalName();
        return replaced ? new ConflictException(AccountErrorCode.PASSWORD_RESET_LINK_REPLACED, reasonForLog)
                : new ConflictException(AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED, reasonForLog);
    }

    private static TableField<PasswordResetTokenRecord, Long> accountColumn(AccountKind kind) {
        return switch (kind) {
            case AUDIENCE -> PASSWORD_RESET_TOKEN.AUDIENCE_ACCOUNT_ID;
            case STAFF -> PASSWORD_RESET_TOKEN.STAFF_ACCOUNT_ID;
            case ADMIN, CHECKIN -> throw notResettable(kind);
        };
    }

    private static IllegalArgumentException notResettable(AccountKind kind) {
        return new IllegalArgumentException("비밀번호 재설정 링크는 관객 계정과 운영진 로그인만 가리킵니다: " + kind);
    }

    private static Condition unused() {
        return PASSWORD_RESET_TOKEN.USED_AT.isNull().and(PASSWORD_RESET_TOKEN.REPLACED_AT.isNull());
    }

    private PasswordResetToken toToken(PasswordResetTokenRecord token) {
        AccountRef account = token.getAudienceAccountId() != null
                ? AccountRef.of(AccountKind.AUDIENCE, token.getAudienceAccountId())
                : AccountRef.of(AccountKind.STAFF, token.getStaffAccountId());
        return PasswordResetToken.restore(token.getToken(), account, token.getIssuedAt().toInstant(),
                token.getExpiresAt().toInstant(), toInstant(token.getUsedAt()), toInstant(token.getReplacedAt()));
    }

    private static Instant toInstant(OffsetDateTime databaseTime) {
        return databaseTime == null ? null : databaseTime.toInstant();
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
