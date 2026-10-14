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
 * 같은 계정의 비밀번호 찾기가 거의 동시에 둘 오면 대체와 저장이 엇갈려 둘 다 살아남을 수 있으므로, 대체할 때 그 계정을 잠가
 * 차례로 처리한다(잠금은 트랜잭션이 끝날 때 풀린다).
 */
public interface PasswordResetTokenRepository {

    /**
     * 그 계정의 링크 가운데 아직 쓰지도 대체되지도 않은 것을 모두 대체된 링크로 바꾼다. 유효 시간이 지난 링크도 바꾼다(어차피 쓸 수 없다).
     * 같은 트랜잭션에서 새 링크를 {@link #add}하기 바로 전에 부른다.
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
}
