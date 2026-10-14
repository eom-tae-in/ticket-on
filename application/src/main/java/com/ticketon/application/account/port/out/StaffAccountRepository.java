package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.error.ConflictException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * 운영진 로그인 계정 저장소 — 나가는 포트.
 * <p>
 * 이메일·닉네임을 쓸 수 있는지는 저장소가 확인해 계정의 규칙({@link StaffAccount#signUp}, {@link StaffAccount#changeNickname})에
 * 넘긴다. 확인과 저장 사이에 같은 이메일이나 닉네임이 다른 요청으로 먼저 저장될 수 있으므로, 저장할 때 다시 막는다({@link #add},
 * {@link #saveNickname}).
 * <ul>
 *   <li>이메일: 탈퇴하지 않은 운영진 로그인 사이에서 하나다(대소문자 무시). 관객 계정은 보지 않는다 — 같은 이메일로 양쪽 가입이 된다
 *       (docs/01 "같은 이메일로 양쪽 가입"). 인증 대기 계정도 이메일을 쥔다.</li>
 *   <li>닉네임: 탈퇴한 계정까지 모든 운영진 계정 사이에서 하나다({@link NicknameAvailability}).</li>
 * </ul>
 */
public interface StaffAccountRepository {

    /** 그 이메일로 운영진 가입을 할 수 있는지. 대소문자를 가리지 않고 같은 이메일로 본다. 운영진 로그인만 본다(관객 계정은 따로다). */
    EmailAvailability emailAvailability(EmailAddress email);

    /** 그 닉네임을 쓸 수 있는지. 이 닉네임을 쥔 계정이 있으면(자기 자신 포함) {@link NicknameAvailability#ALREADY_TAKEN}. */
    NicknameAvailability nicknameAvailability(Nickname nickname);

    /** 주어진 닉네임 가운데 이미 운영진 계정이 쥔 것 — 랜덤 닉네임을 고를 때 쓰는 것을 한 번에 뺀다. */
    Set<Nickname> takenNicknamesAmong(Collection<Nickname> nicknames);

    /**
     * 막 가입한 계정을 저장하고, id가 붙은 계정을 돌려준다.
     *
     * @param newAccount {@link StaffAccount#signUp}으로 만든, id가 없는 계정
     * @throws ConflictException 확인한 뒤 같은 이메일({@code STAFF_EMAIL_ALREADY_REGISTERED})이나
     *                           같은 닉네임({@code STAFF_NICKNAME_ALREADY_TAKEN})의 계정이 먼저 저장됐으면 — 동시 가입 경합
     */
    StaffAccount add(StaffAccount newAccount);

    Optional<StaffAccount> findById(long id);

    /**
     * 그 이메일로 가입한 운영진 로그인 — 로그인 아이디로 찾는다. 대소문자를 가리지 않는다. 없으면 비어 있다.
     * <p>
     * 탈퇴한 계정은 찾지 않는다. 인증 대기 계정은 찾는다 — 로그인할 수 있는지는 계정이 판단한다({@link StaffAccount#canLogIn}).
     */
    Optional<StaffAccount> findRegisteredByEmail(EmailAddress email);

    /**
     * 이메일 인증을 마친 계정의 상태와 인증 일시를 저장한다. 저장소의 계정이 아직 인증 대기일 때만 바꾼다.
     *
     * @param verifiedAccount {@link StaffAccount#verifyEmail}로 인증을 마친 계정
     * @throws ConflictException 그 사이 다른 요청이 먼저 인증을 마쳤으면({@code EMAIL_ALREADY_VERIFIED})
     */
    void saveEmailVerification(StaffAccount verifiedAccount);

    /**
     * 바꾼 닉네임을 저장한다. 저장소의 계정이 활동 중일 때만 바꾼다.
     *
     * @param changedAccount {@link StaffAccount#changeNickname}으로 닉네임을 바꾼 계정
     * @throws ConflictException 확인한 뒤 다른 운영진이 같은 닉네임을 먼저 저장했거나({@code STAFF_NICKNAME_ALREADY_TAKEN}),
     *                           그 사이 계정이 활동 중이 아니게 됐으면({@code STAFF_NOT_ACTIVE})
     */
    void saveNickname(StaffAccount changedAccount);
}
