package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.GetMyStaffProfileQuery;
import com.ticketon.application.account.port.in.MyStaffProfileResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffIntroduction;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("내 프로필 조회 유스케이스")
class GetMyStaffProfileServiceTest {

    private final FakeStaffAccountRepository accountRepository = new FakeStaffAccountRepository();
    private final StoredStaffAccounts accounts = new StoredStaffAccounts(accountRepository);

    private final GetMyStaffProfileService service = new GetMyStaffProfileService(accountRepository);

    @Test
    @DisplayName("닉네임 · 한 줄 소개 · 첫 글자와 팔레트 번호(계정 id로 정한 8가지 중 하나)를 돌려준다 — 이름 · 이메일은 돌려주지 않는다")
    void returnsProfile() {
        long id = accounts.active("junghoon@example.com", "조용한고래");
        StaffAccount account = accountRepository.findById(id).orElseThrow();
        account.changeIntroduction(new StaffIntroduction("극단에서 연출하고 있습니다"));
        accountRepository.saveProfile(account);

        MyStaffProfileResult result = service.getMyProfile(query(id));

        assertThat(result).isEqualTo(
                new MyStaffProfileResult(id, "조용한고래", "극단에서 연출하고 있습니다", "조", (int) (id % 8)));
    }

    @Test
    @DisplayName("소개를 적지 않았으면 소개는 비어 있다(null)")
    void noIntroduction() {
        long id = accounts.active("junghoon@example.com", "조용한고래");

        assertThat(service.getMyProfile(query(id)).introduction()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 계정은 보여 주지 않는다 — 탈퇴는 세션을 끊으므로 보통은 생기지 않는다")
    void rejectsWithdrawn() {
        long id = accounts.withdrawn("junghoon@example.com", "조용한고래");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.getMyProfile(query(id)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.STAFF_WITHDRAWN);
    }

    private static GetMyStaffProfileQuery query(long staffAccountId) {
        return new GetMyStaffProfileQuery(AccountRef.of(AccountKind.STAFF, staffAccountId));
    }
}
