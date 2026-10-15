package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.error.ConflictException;
import java.time.Instant;
import java.util.Optional;

/**
 * 비밀번호 재설정 링크 저장소 — 나가는 포트. 쓴 링크·대체된 링크도 지우지 않는다({@link PasswordResetToken}).
 * <p>
 * 쓸 수 있는 링크는 계정마다 많아야 하나다. 새 링크를 저장하기 전에 {@link #replaceUnusedTokensOf}로 이전 링크를 대체한다.
 *
 * <h2>잠금 순서 — 계정 → 링크</h2>
 * 링크나 그 계정 줄을 바꾸는 트랜잭션은 <b>무엇이든 바꾸기 전에 맨 먼저 {@link #lockAccount}로 그 계정을 잠근다.</b>
 * <ul>
 *   <li>비밀번호 찾기: 계정 잠금 → 이전 링크 대체 → 새 링크 저장</li>
 *   <li>비밀번호 재설정: 계정 잠금 → 링크 사용 저장 → 계정 비밀번호 저장</li>
 *   <li>탈퇴: 계정 잠금 → 계정 줄 저장 → 링크 지우기</li>
 * </ul>
 * 같은 계정의 두 요청은 잠금에서 차례를 기다린다. 비밀번호 찾기 둘이 엇갈려 쓸 수 있는 링크가 둘 남는 일이 없고, 찾기와 재설정이
 * 서로 반대 순서로 줄을 잠가 교착(PostgreSQL 40P01)으로 한쪽이 끊기는 일도 없다. 잠금은 트랜잭션이 끝날 때 풀린다.
 */
public interface PasswordResetTokenRepository {

    /**
     * 그 계정의 재설정 링크와 계정 줄을 바꾸는 일을 이 트랜잭션이 끝날 때까지 혼자 하도록 그 계정을 잠근다. 다른 트랜잭션이 잠가 두었으면
     * 그 트랜잭션이 끝날 때까지 기다린다. 링크나 계정 줄을 바꾸기 전에 맨 먼저 부른다(위 "잠금 순서").
     *
     * @param account 링크를 바꿀 계정 — 관객 계정 또는 운영진 로그인
     */
    void lockAccount(AccountRef account);

    /**
     * 그 계정의 링크 가운데 아직 쓰지도 대체되지도 않은 것을 모두 대체된 링크로 바꾼다. 유효 시간이 지난 링크도 바꾼다(어차피 쓸 수 없다).
     * 같은 트랜잭션에서 {@link #lockAccount}로 계정을 잠근 뒤, 새 링크를 {@link #add}하기 바로 전에 부른다.
     *
     * @param account    비밀번호 찾기를 한 계정
     * @param replacedAt 새 링크를 보내는 일시
     * @return 대체한 링크 수
     */
    int replaceUnusedTokensOf(AccountRef account, Instant replacedAt);

    /** 막 만든 링크를 저장한다. */
    void add(PasswordResetToken token);

    /**
     * 링크에 담긴 값으로 찾는다. 그 종류의 계정을 가리키는 링크만 찾는다 — 관객 쪽에서 운영진 로그인 링크의 값을 넣으면(그 반대도)
     * 발급한 적 없는 값처럼 비어 있다.
     */
    Optional<PasswordResetToken> findByValue(AccountKind accountKind, String value);

    /**
     * 비밀번호를 바꾼 링크의 쓴 일시를 저장한다. 저장소의 링크가 아직 쓰지도 대체되지도 않았을 때만 바꾼다.
     *
     * @param usedToken {@link PasswordResetToken#resetPassword}로 비밀번호를 바꾼 링크
     * @throws ConflictException 그 사이 같은 링크로 다른 요청이 먼저 바꿨거나({@code PASSWORD_RESET_LINK_ALREADY_USED}),
     *                           비밀번호 찾기를 다시 해 새 링크로 대체됐으면({@code PASSWORD_RESET_LINK_REPLACED})
     */
    void saveUse(PasswordResetToken usedToken);

    /**
     * 그 계정의 재설정 링크를 모두 지운다(쓴 링크 · 대체된 링크 포함) — 계정을 탈퇴할 때(회원 탈퇴 시 법정 보존 대상 외 즉시 파기,
     * docs/11 "데이터 보관"). 같은 트랜잭션에서 {@link #lockAccount}로 계정을 잠근 뒤에 부른다(위 "잠금 순서").
     *
     * @param account 탈퇴하는 계정 — 관객 계정 또는 운영진 로그인
     * @return 지운 링크 수
     */
    int deleteAllOf(AccountRef account);
}
