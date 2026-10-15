package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 운영진 로그인 탈퇴 — 들어오는 포트(docs/01 "탈퇴와 주최자 닫기").
 * <ul>
 *   <li><b>운영자</b>는 언제든 탈퇴한다. 소속된 주최자마다 빠지고(소속 이력은 남는다), 그 주최자의 운영진 조작 기록에 남는다.</li>
 *   <li><b>소유자</b>로 남은 주최자가 있으면 막는다 — 먼저 소유자를 넘기거나 주최자를 닫아야 한다.</li>
 *   <li>탈퇴하면 그 계정의 로그인 세션을 모두 바로 끊는다(docs/01, docs/16 "로그인 — 정지·탈퇴는 즉시 끊겨야 함").</li>
 *   <li>이름 · 이메일 · 비밀번호 해시를 바로 파기하고(docs/11 "데이터 보관 — 회원 탈퇴 시"), 닉네임은 남겨 다른 사람이 가져가지 못하게 한다.
 *       이메일은 풀려 같은 이메일로 다시 가입할 수 있다. 그 계정의 이메일 인증 링크와 비밀번호 재설정 링크도 지운다.</li>
 * </ul>
 * 탈퇴한 계정으로는 다시 로그인할 수 없다(로그인 실패는 까닭과 상관없이 한 가지로 알린다).
 */
public interface WithdrawStaffAccountUseCase {

    /**
     * @throws RuleViolationException 소유자인 주최자가 남아 있으면({@link OrganizerErrorCode#OWNER_MUST_HAND_OVER_FIRST}),
     *                                이미 탈퇴한 계정이면({@link AccountErrorCode#STAFF_WITHDRAWN})
     * @throws ConflictException      같은 계정의 탈퇴가 거의 동시에 두 번 들어와 한쪽이 먼저 탈퇴시켰으면({@link AccountErrorCode#STAFF_WITHDRAWN}),
     *                                탈퇴하는 사이 그 소속이 먼저 끝났으면 — 소유자가 내보냈거나 소유자를 넘겨받는 중
     *                                ({@link OrganizerErrorCode#MEMBERSHIP_ALREADY_ENDED})
     */
    void withdraw(WithdrawStaffAccountCommand command);
}
