package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.PasswordResetTokenRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.error.ConflictException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 비밀번호 재설정 링크 저장소 — 저장소 약속을 표처럼 지킨다: 쓸 수 있는 링크는 계정마다 하나, 종류가 다른 쪽에서는 찾지 못함,
 * 읽을 때마다 새 객체(저장하지 않은 변경은 남지 않음), 사용 저장은 쓰지도 대체되지도 않았을 때만.
 * <p>
 * 계정 잠금과 링크를 바꾼 일을 부른 차례대로 쓰기 기록({@link WriteLog})에 남긴다 — 잠금 순서(계정 → 링크)를 보려고.
 */
final class FakePasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final Map<String, PasswordResetToken> tokens = new LinkedHashMap<>();
    private final WriteLog writeLog;

    FakePasswordResetTokenRepository() {
        this(new WriteLog());
    }

    FakePasswordResetTokenRepository(WriteLog writeLog) {
        this.writeLog = writeLog;
    }

    /** 저장된 링크 전부 — 저장한 순서대로. */
    List<PasswordResetToken> savedTokens() {
        return tokens.values().stream().map(FakePasswordResetTokenRepository::copyOf).toList();
    }

    @Override
    public void lockAccount(AccountRef account) {
        writeLog.record("계정 잠금 " + account.principalName());
    }

    @Override
    public int replaceUnusedTokensOf(AccountRef account, Instant replacedAt) {
        writeLog.record("링크 대체 " + account.principalName());
        List<PasswordResetToken> unusedTokens = tokens.values().stream()
                .filter(token -> token.account().equals(account) && isUnused(token))
                .toList();
        unusedTokens.forEach(token -> tokens.put(token.value(), PasswordResetToken.restore(token.value(),
                token.account(), token.issuedAt(), token.expiresAt(), null, replacedAt)));
        return unusedTokens.size();
    }

    @Override
    public void add(PasswordResetToken token) {
        boolean anotherUnused = tokens.values().stream()
                .anyMatch(saved -> saved.account().equals(token.account()) && isUnused(saved));
        if (anotherUnused) {
            throw new IllegalStateException("쓸 수 있는 링크는 계정마다 하나입니다 — 이전 링크를 먼저 대체하세요: " + token.account());
        }
        writeLog.record("링크 저장 " + token.account().principalName());
        tokens.put(token.value(), copyOf(token));
    }

    @Override
    public Optional<PasswordResetToken> findByValue(AccountKind accountKind, String value) {
        return Optional.ofNullable(tokens.get(value))
                .filter(token -> token.account().kind() == accountKind)
                .map(FakePasswordResetTokenRepository::copyOf);
    }

    @Override
    public void saveUse(PasswordResetToken usedToken) {
        PasswordResetToken saved = tokens.get(usedToken.value());
        if (saved.replacedAt().isPresent()) {
            throw new ConflictException(AccountErrorCode.PASSWORD_RESET_LINK_REPLACED, "사용 경합(흉내)");
        }
        if (saved.usedAt().isPresent()) {
            throw new ConflictException(AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED, "사용 경합(흉내)");
        }
        writeLog.record("링크 사용 " + usedToken.account().principalName());
        tokens.put(usedToken.value(), copyOf(usedToken));
    }

    private static boolean isUnused(PasswordResetToken token) {
        return token.usedAt().isEmpty() && token.replacedAt().isEmpty();
    }

    private static PasswordResetToken copyOf(PasswordResetToken token) {
        return PasswordResetToken.restore(token.value(), token.account(), token.issuedAt(), token.expiresAt(),
                token.usedAt().orElse(null), token.replacedAt().orElse(null));
    }
}
