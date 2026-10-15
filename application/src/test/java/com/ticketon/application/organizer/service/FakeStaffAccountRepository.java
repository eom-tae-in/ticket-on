package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffPersonalInformation;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 메모리 운영진 계정 저장소 — 운영자 초대 · 내보내기가 쓰는 찾기(id · 이메일)만 한다. 가입 · 인증 · 닉네임 · 비밀번호 저장은 계정 유스케이스의
 * 테스트가 본다.
 */
final class FakeStaffAccountRepository implements StaffAccountRepository {

    private static final Instant SIGNED_UP_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 1, 10, 0));

    private final Map<Long, StaffAccount> accounts = new LinkedHashMap<>();

    /** 이메일 인증을 마친 운영진 로그인을 그 id로 바로 둔다. */
    void storeActive(long id, String name, String nickname, String email) {
        accounts.put(id, StaffAccount.restore(id, StaffPersonalInformation.of(name, email), Nickname.of(nickname),
                new PasswordHash("{test}hashed-password"), SIGNED_UP_AT, StaffAccountStatus.ACTIVE, SIGNED_UP_AT));
    }

    /** 그 운영진 로그인을 탈퇴한 것으로 바꾼다 — 개인정보는 없고 닉네임만 남는다. */
    void markWithdrawn(long id) {
        StaffAccount account = accounts.get(id);
        accounts.put(id, StaffAccount.restoreWithdrawn(id, account.nickname(), account.signedUpAt(),
                account.signedUpAt().plusSeconds(86_400)));
    }

    @Override
    public Optional<StaffAccount> findById(long id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Optional<StaffAccount> findRegisteredByEmail(EmailAddress email) {
        return accounts.values().stream()
                .filter(account -> account.status() != StaffAccountStatus.WITHDRAWN)
                .filter(account -> lowercase(account.personalInformation().email()).equals(lowercase(email)))
                .findFirst();
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        throw new UnsupportedOperationException("운영자 초대에서는 가입하지 않습니다");
    }

    @Override
    public NicknameAvailability nicknameAvailability(Nickname nickname) {
        throw new UnsupportedOperationException("운영자 초대에서는 닉네임을 보지 않습니다");
    }

    @Override
    public Set<Nickname> takenNicknamesAmong(Collection<Nickname> nicknames) {
        throw new UnsupportedOperationException("운영자 초대에서는 닉네임을 보지 않습니다");
    }

    @Override
    public StaffAccount add(StaffAccount newAccount) {
        throw new UnsupportedOperationException("운영자 초대에서는 가입하지 않습니다");
    }

    @Override
    public void saveEmailVerification(StaffAccount verifiedAccount) {
        throw new UnsupportedOperationException("운영자 초대에서는 이메일 인증을 저장하지 않습니다");
    }

    @Override
    public void saveNickname(StaffAccount changedAccount) {
        throw new UnsupportedOperationException("운영자 초대에서는 닉네임을 저장하지 않습니다");
    }

    @Override
    public void savePassword(StaffAccount changedAccount) {
        throw new UnsupportedOperationException("운영자 초대에서는 비밀번호를 저장하지 않습니다");
    }

    @Override
    public void saveWithdrawal(StaffAccount withdrawnAccount) {
        throw new UnsupportedOperationException("주최자 쪽 유스케이스에서는 탈퇴를 저장하지 않습니다");
    }

    private static String lowercase(EmailAddress email) {
        return email.value().toLowerCase(Locale.ROOT);
    }
}
