package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.error.ConflictException;
import java.util.Optional;

/**
 * 관리자 계정 저장소 — 나가는 포트.
 * <p>
 * 이메일은 관리자 계정 사이에서 하나다(대소문자 무시). 관객 계정·운영진 로그인은 보지 않는다. 쓸 수 있는지는 저장소가 확인해 발급 규칙
 * ({@link AdminAccount#issue})에 넘기고, 확인과 저장 사이에 같은 이메일이 다른 요청으로 먼저 저장될 수 있으므로 저장할 때 다시 막는다
 * ({@link #add}).
 */
public interface AdminAccountRepository {

    /** 그 이메일로 관리자 계정을 발급할 수 있는지. 대소문자를 가리지 않고 같은 이메일로 본다. 관리자 계정만 본다. */
    EmailAvailability emailAvailability(EmailAddress email);

    /**
     * 막 발급한 계정을 저장하고, id가 붙은 계정을 돌려준다.
     *
     * @param newAccount {@link AdminAccount#issue}로 만든, id가 없는 계정
     * @throws ConflictException 확인한 뒤 같은 이메일의 관리자 계정이 먼저 저장됐으면({@code ADMIN_EMAIL_ALREADY_REGISTERED}) —
     *                           동시 발급 경합
     */
    AdminAccount add(AdminAccount newAccount);

    Optional<AdminAccount> findById(long id);

    /** 그 이메일의 관리자 계정 — 로그인 아이디로 찾는다. 대소문자를 가리지 않는다. 없으면 비어 있다. */
    Optional<AdminAccount> findByEmail(EmailAddress email);
}
