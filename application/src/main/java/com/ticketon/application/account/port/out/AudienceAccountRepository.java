package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.error.ConflictException;
import java.util.Optional;

/**
 * 관객 계정 저장소 — 나가는 포트.
 * <p>
 * 번호·이메일을 쓸 수 있는지는 저장소가 확인해 가입 규칙({@link AudienceAccount#signUp})에 넘긴다. 확인과 저장 사이에 같은 번호나
 * 이메일로 다른 가입이 먼저 저장될 수 있으므로, 저장할 때 다시 막는다({@link #add}).
 * <p>
 * '가입한 계정'은 탈퇴하지 않은 계정 — 인증 대기·활동·이용 정지 — 이다. 인증 대기 계정도 번호와 이메일을 쥔다
 * (docs/01 "같은 번호로 가입한 계정이 있으면 가입 불가", "일반 탈퇴 — 번호가 풀려 다시 가입할 수 있음").
 */
public interface AudienceAccountRepository {

    /**
     * 그 번호로 가입할 수 있는지. 가입한 계정이 쓰고 있으면 {@link PhoneNumberAvailability#ALREADY_REGISTERED},
     * 이용 정지 후 탈퇴로 차단 목록에 남은 번호면 {@link PhoneNumberAvailability#BLOCKED}다.
     */
    PhoneNumberAvailability phoneNumberAvailability(PhoneNumber phoneNumber);

    /** 그 이메일로 가입할 수 있는지. 대소문자를 가리지 않고 같은 이메일로 본다. 관객 계정만 본다(운영진 로그인은 따로다). */
    EmailAvailability emailAvailability(EmailAddress email);

    /**
     * 막 가입한 계정과 그 동의 기록을 저장하고, id가 붙은 계정을 돌려준다.
     *
     * @param newAccount {@link AudienceAccount#signUp}으로 만든, id가 없는 계정
     * @throws ConflictException 확인한 뒤 같은 번호({@code PHONE_NUMBER_ALREADY_REGISTERED})나
     *                           같은 이메일({@code AUDIENCE_EMAIL_ALREADY_REGISTERED})의 계정이 먼저 저장됐으면 — 동시 가입 경합
     */
    AudienceAccount add(AudienceAccount newAccount);

    Optional<AudienceAccount> findById(long id);

    /**
     * 이메일 인증을 마친 계정의 상태와 인증 일시를 저장한다. 저장소의 계정이 아직 인증 대기일 때만 바꾼다.
     *
     * @param verifiedAccount {@link AudienceAccount#verifyEmail}로 인증을 마친 계정
     * @throws ConflictException 그 사이 다른 요청이 먼저 인증을 마쳤으면({@code EMAIL_ALREADY_VERIFIED})
     */
    void saveEmailVerification(AudienceAccount verifiedAccount);
}
