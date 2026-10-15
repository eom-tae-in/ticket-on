package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 관객 전화번호 변경 — 들어오는 포트(docs/01 "전화번호 — 번호 변경 (마이페이지)", docs/13 "계정 정보 — 전화번호 변경").
 * <p>
 * 가입과 같은 확인을 거친다 — 번호 하나에 계정 하나. 다른 계정이 쓰는 번호나 차단된 번호로는 바꿀 수 없다. 번호는 인증하지 않는다
 * (docs/12 "전화번호는 인증하지 않습니다"). 지금 번호와 같으면 아무것도 바꾸지 않고 그대로 돌려준다. 이용 정지 중에도 바꿀 수 있다.
 */
public interface ChangeAudiencePhoneNumberUseCase {

    /**
     * @throws RuleViolationException 다른 계정이 쓰는 번호 또는 차단된 번호(PHONE_NUMBER_ALREADY_REGISTERED), 인증 대기·탈퇴 계정
     *                                (AUDIENCE_NOT_ACTIVE) — 인증 전·탈퇴 뒤에는 로그인할 수 없으므로 보통은 생기지 않는다
     * @throws ConflictException      같은 번호로 거의 동시에 바꾸거나 가입한 다른 요청이 먼저 저장됐으면 — 문구는 위와 같다
     */
    ChangeAudiencePhoneNumberResult changePhoneNumber(ChangeAudiencePhoneNumberCommand command);
}
