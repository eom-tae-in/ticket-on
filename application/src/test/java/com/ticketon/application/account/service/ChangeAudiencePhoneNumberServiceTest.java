package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberCommand;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("관객 전화번호 변경 유스케이스 — 번호 하나에 계정 하나 (docs/01)")
class ChangeAudiencePhoneNumberServiceTest {

    private static final String SEOYUN_EMAIL = "seoyun@example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final String NEW_PHONE = "010-9876-5432";

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final ChangeAudiencePhoneNumberService service = new ChangeAudiencePhoneNumberService(accountRepository);

    @Test
    @DisplayName("아무도 쓰지 않는 번호로 바꿔 저장하고 숫자만 남긴 새 번호를 돌려준다 — 옛 번호는 다른 가입에 쓸 수 있게 풀린다")
    void changesAndSavesPhoneNumber() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

        ChangeAudiencePhoneNumberResult result = service.changePhoneNumber(command(id, NEW_PHONE));

        assertThat(result.phoneNumber()).isEqualTo("01098765432");
        assertThat(accountRepository.findById(id).orElseThrow().personalInformation().phoneNumber())
                .isEqualTo(PhoneNumber.of(NEW_PHONE));
        assertThat(accountRepository.phoneNumberAvailability(PhoneNumber.of(SEOYUN_PHONE)))
                .isEqualTo(PhoneNumberAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("다른 계정이 쓰는 번호로는 바꿀 수 없다 — \"이미 가입된 번호입니다…\" 문구, 번호는 그대로다")
    void rejectsNumberOfAnotherAccount() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        accounts.active("dohyun@example.com", NEW_PHONE);

        assertRejected(id, NEW_PHONE, AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        assertThat(accountRepository.findById(id).orElseThrow().personalInformation().phoneNumber())
                .isEqualTo(PhoneNumber.of(SEOYUN_PHONE));
    }

    @Test
    @DisplayName("이메일 인증 전인 계정이 쥔 번호와 이용 정지된 계정의 번호로도 바꿀 수 없다 — 둘 다 가입한 계정이다")
    void rejectsNumberOfPendingOrSuspendedAccount() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        accounts.pending("pending@example.com", NEW_PHONE);
        accounts.suspended("suspended@example.com", "010-5555-6666");

        assertRejected(id, NEW_PHONE, AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        assertRejected(id, "010-5555-6666", AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("차단된 번호로는 바꿀 수 없고, 가입된 번호와 같은 문구다 — 문구가 다르면 정지 이력이 드러난다")
    void rejectsBlockedNumberWithSameMessage() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        accountRepository.block(PhoneNumber.of(NEW_PHONE));

        assertRejected(id, NEW_PHONE, AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("일반 탈퇴한 계정이 쓰던 번호로는 바꿀 수 있다 — 탈퇴하면 번호가 풀린다")
    void numberOfWithdrawnAccountIsFree() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        accounts.withdrawn("left@example.com", NEW_PHONE);

        assertThat(service.changePhoneNumber(command(id, NEW_PHONE)).phoneNumber()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("지금 번호를 다르게 적어도 같은 번호면 저장하지 않고 그대로 돌려준다")
    void sameNumberIsNotSaved() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

        ChangeAudiencePhoneNumberResult result = service.changePhoneNumber(command(id, "01012345678"));

        assertThat(result.phoneNumber()).isEqualTo("01012345678");
        assertThat(accountRepository.accountWrites()).isEmpty();
    }

    @Test
    @DisplayName("이용 정지 중에도 바꿀 수 있다 — 정지로 막히는 것은 새 예매·변경·신고뿐이다 (docs/01)")
    void suspendedAccountCanChange() {
        long id = accounts.suspended(SEOYUN_EMAIL, SEOYUN_PHONE);

        assertThat(service.changePhoneNumber(command(id, NEW_PHONE)).phoneNumber()).isEqualTo("01098765432");
        assertThat(accountRepository.findById(id).orElseThrow().isSuspended()).isTrue();
    }

    @Test
    @DisplayName("확인은 통과했지만 저장 직전 다른 계정이 같은 번호를 먼저 저장하면 충돌이 그대로 나간다")
    void propagatesPhoneNumberRaceConflict() {
        long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);
        accountRepository.letAnotherAccountTakeNextPhoneNumber();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.changePhoneNumber(command(id, NEW_PHONE)))
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("관객이 아닌 계정으로는 Command를 만들 수 없다")
    void commandRequiresAudience() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new ChangeAudiencePhoneNumberCommand(
                AccountRef.of(AccountKind.STAFF, 1), PhoneNumber.of(NEW_PHONE)));
    }

    private void assertRejected(long accountId, String phoneNumber, AccountErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.changePhoneNumber(command(accountId, phoneNumber)))
                .extracting(DomainException::errorCode)
                .isEqualTo(expectedCode);
    }

    private static ChangeAudiencePhoneNumberCommand command(long accountId, String phoneNumber) {
        return new ChangeAudiencePhoneNumberCommand(AccountRef.of(AccountKind.AUDIENCE, accountId), PhoneNumber.of(phoneNumber));
    }
}
