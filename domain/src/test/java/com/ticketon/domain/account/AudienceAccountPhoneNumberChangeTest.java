package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.ADULT_BIRTH_DATE;
import static com.ticketon.domain.account.AudienceAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.activeAccount;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static com.ticketon.domain.account.AudienceAccountFixtures.koreanTime;
import static com.ticketon.domain.account.AudienceAccountFixtures.newlySignedUpAccount;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("관객 전화번호 변경 — 번호 하나에 계정 하나 (docs/01 \"전화번호 — 번호 변경 (마이페이지)\")")
class AudienceAccountPhoneNumberChangeTest {

    /** 가입할 때 적은 번호 — 010-1234-5678. */
    private static final PhoneNumber SIGNUP_NUMBER = PhoneNumber.of("010-1234-5678");

    private static final PhoneNumber NEW_NUMBER = PhoneNumber.of("010-9876-5432");

    @Test
    @DisplayName("아무도 쓰지 않는 번호로 바꾸면 번호만 바뀌고 이름·생년월일·이메일은 그대로다")
    void changesOnlyPhoneNumber() {
        AudienceAccount account = activeAccount();

        boolean changed = account.changePhoneNumber(NEW_NUMBER, PhoneNumberAvailability.AVAILABLE);

        assertThat(changed).isTrue();
        assertThat(account.personalInformation()).isEqualTo(new AudiencePersonalInformation("한서윤", ADULT_BIRTH_DATE,
                EmailAddress.of("seoyun@example.com"), PhoneNumber.of("01098765432")));
    }

    @Test
    @DisplayName("지금 번호를 공백을 넣어 다르게 적어도 같은 번호라 그대로다 — 저장소가 '가입된 번호'라 답해도 자기 번호라 막지 않는다")
    void sameNumberWrittenDifferentlyIsUnchanged() {
        AudienceAccount account = activeAccount();

        boolean changed = account.changePhoneNumber(PhoneNumber.of("010 1234 5678"),
                PhoneNumberAvailability.ALREADY_REGISTERED);

        assertThat(changed).isFalse();
        assertThat(account.personalInformation().phoneNumber()).isEqualTo(SIGNUP_NUMBER);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PhoneNumberAvailability.class, names = {"ALREADY_REGISTERED", "BLOCKED"})
    @DisplayName("다른 계정이 쓰는 번호나 차단된 번호로는 바꿀 수 없고, 둘 다 가입과 같은 문구다 — 문구가 다르면 정지 이력이 드러난다")
    void rejectsTakenOrBlockedNumber(PhoneNumberAvailability availability) {
        AudienceAccount account = activeAccount();

        assertRejected(() -> account.changePhoneNumber(NEW_NUMBER, availability),
                AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        assertThat(account.personalInformation().phoneNumber()).isEqualTo(SIGNUP_NUMBER);
        assertThat(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED.message())
                .isEqualTo("이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요.");
    }

    @Test
    @DisplayName("이용 정지 중에는 바꿀 수 없다 — 번호를 바꾸고 탈퇴해 진짜 번호를 풀면 다시 가입해 정지를 피할 수 있다 (docs/01 \"관객 이용 정지\")")
    void suspendedAccountCannotChange() {
        AudienceAccount account = activeAccount();
        account.suspend("번호를 바꿔가며 1인 매수 제한을 넘는 예매", koreanTime(2026, 11, 2, 14, 0));

        assertRejected(() -> account.changePhoneNumber(NEW_NUMBER, PhoneNumberAvailability.AVAILABLE),
                AccountErrorCode.AUDIENCE_SUSPENDED);
        assertThat(account.personalInformation().phoneNumber()).isEqualTo(SIGNUP_NUMBER);
        assertThat(AccountErrorCode.AUDIENCE_SUSPENDED.message()).isEqualTo("이용 정지 중에는 이용할 수 없는 기능입니다.");
    }

    @Test
    @DisplayName("정지가 풀리면 다시 바꿀 수 있다")
    void liftedAccountCanChangeAgain() {
        AudienceAccount account = activeAccount();
        account.suspend("번호를 바꿔가며 1인 매수 제한을 넘는 예매", koreanTime(2026, 11, 2, 14, 0));
        account.lift(koreanTime(2026, 12, 1, 9, 30));

        assertThat(account.changePhoneNumber(NEW_NUMBER, PhoneNumberAvailability.AVAILABLE)).isTrue();
        assertThat(account.personalInformation().phoneNumber()).isEqualTo(NEW_NUMBER);
    }

    @Test
    @DisplayName("인증 대기 계정은 바꿀 수 없다 — 로그인할 수 없는 상태다")
    void pendingAccountCannotChange() {
        AudienceAccount account = newlySignedUpAccount();

        assertRejected(() -> account.changePhoneNumber(NEW_NUMBER, PhoneNumberAvailability.AVAILABLE),
                AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        assertThat(account.personalInformation().phoneNumber()).isEqualTo(SIGNUP_NUMBER);
    }

    @Test
    @DisplayName("탈퇴한 계정은 바꿀 수 없다")
    void withdrawnAccountCannotChange() {
        AudienceAccount account = AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNUP_TIME), AudienceAccountStatus.WITHDRAWN,
                SIGNUP_TIME.plusSeconds(300), List.of());

        assertRejected(() -> account.changePhoneNumber(NEW_NUMBER, PhoneNumberAvailability.AVAILABLE),
                AccountErrorCode.AUDIENCE_NOT_ACTIVE);
    }
}
