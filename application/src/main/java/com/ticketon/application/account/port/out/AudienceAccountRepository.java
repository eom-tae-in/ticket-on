package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.error.ConflictException;
import java.util.Optional;

/**
 * 관객 계정 저장소 — 나가는 포트.
 * <p>
 * 번호·이메일을 쓸 수 있는지는 저장소가 확인해 가입 규칙({@link AudienceAccount#signUp})과 번호 변경 규칙
 * ({@link AudienceAccount#changePhoneNumber})에 넘긴다. 확인과 저장 사이에 같은 번호나 이메일로 다른 가입·번호 변경이 먼저 저장될 수
 * 있으므로, 저장할 때 다시 막는다({@link #add}, {@link #savePhoneNumber}).
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
     * 그 계정을 이 트랜잭션이 끝날 때까지 잠그고 읽는다. 다른 트랜잭션이 잠가 두었으면 끝날 때까지 기다렸다가, 그 트랜잭션이 남긴 것까지 읽는다.
     * <p>
     * 마케팅 수신 동의를 바꿀 때 쓴다 — 같은 관객이 거의 동시에 두 번 철회해도 뒤에 온 요청은 앞 요청이 더한 줄을 보고 아무것도 더하지 않아,
     * 기록과 처리 결과 안내가 두 번 남지 않는다.
     */
    Optional<AudienceAccount> findByIdForUpdate(long id);

    /**
     * 그 이메일로 가입한 계정 — 로그인 아이디로 찾는다. 대소문자를 가리지 않는다. 없으면 비어 있다.
     * <p>
     * 탈퇴한 계정은 찾지 않는다. 탈퇴하면 이메일이 풀려 같은 이메일로 새로 가입할 수 있으므로, 가입한 계정은 이메일마다 많아야 하나다.
     * 인증 대기 계정은 찾는다 — 로그인할 수 있는지는 계정이 판단한다({@link AudienceAccount#can}).
     */
    Optional<AudienceAccount> findRegisteredByEmail(EmailAddress email);

    /**
     * 이메일 인증을 마친 계정의 상태와 인증 일시를 저장한다. 저장소의 계정이 아직 인증 대기일 때만 바꾼다.
     *
     * @param verifiedAccount {@link AudienceAccount#verifyEmail}로 인증을 마친 계정
     * @throws ConflictException 그 사이 다른 요청이 먼저 인증을 마쳤으면({@code EMAIL_ALREADY_VERIFIED})
     */
    void saveEmailVerification(AudienceAccount verifiedAccount);

    /**
     * 비밀번호 찾기로 새로 정한 비밀번호 해시를 저장한다. 저장소의 계정이 비밀번호를 바꿀 수 있는 상태(활동·이용 정지)일 때만 바꾼다.
     *
     * @param changedAccount {@link AudienceAccount#resetPassword}로 비밀번호를 바꾼 계정
     * @throws ConflictException 그 사이 계정이 탈퇴했으면({@code AUDIENCE_NOT_ACTIVE})
     */
    void savePassword(AudienceAccount changedAccount);

    /**
     * 바꾼 전화번호를 저장한다. 저장소의 계정이 번호를 바꿀 수 있는 상태(활동·이용 정지)일 때만 바꾼다.
     * <p>
     * 확인({@link #phoneNumberAvailability})과 저장 사이에 같은 번호로 다른 가입이나 다른 계정의 번호 변경이 먼저 저장될 수 있으므로,
     * 저장할 때 다시 막는다 — 가입한 계정 사이에서 번호는 하나다.
     *
     * @param changedAccount {@link AudienceAccount#changePhoneNumber}로 번호를 바꾼 계정
     * @throws ConflictException 확인한 뒤 다른 계정이 같은 번호를 먼저 저장했거나({@code PHONE_NUMBER_ALREADY_REGISTERED}),
     *                           그 사이 계정이 탈퇴했으면({@code AUDIENCE_NOT_ACTIVE})
     */
    void savePhoneNumber(AudienceAccount changedAccount);

    /**
     * 동의 기록 한 줄을 더한다. 이전 기록은 고치지 않는다 — 한 항목의 지금 상태는 가장 최근 줄이다.
     *
     * @param audienceAccountId 저장된 관객 계정
     * @param consentRecord     {@link AudienceAccount#changeMarketingConsent}가 돌려준 새 기록
     */
    void addConsentRecord(long audienceAccountId, ConsentRecord consentRecord);
}
