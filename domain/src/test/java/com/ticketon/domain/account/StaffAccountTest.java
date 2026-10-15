package com.ticketon.domain.account;

import static com.ticketon.domain.account.StaffAccountFixtures.INTRODUCTION;
import static com.ticketon.domain.account.StaffAccountFixtures.NICKNAME;
import static com.ticketon.domain.account.StaffAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.StaffAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.StaffAccountFixtures.assertRejected;
import static com.ticketon.domain.account.StaffAccountFixtures.newlySignedUpAccount;
import static com.ticketon.domain.account.StaffAccountFixtures.personalInformation;
import static com.ticketon.domain.account.StaffAccountFixtures.storedActiveAccount;
import static com.ticketon.domain.account.StaffAccountFixtures.storedActiveAccountWithIntroduction;
import static com.ticketon.domain.account.StaffAccountFixtures.storedPendingAccount;
import static com.ticketon.domain.account.StaffAccountFixtures.storedWithdrawnAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("운영진 로그인 계정")
class StaffAccountTest {

    @Nested
    @DisplayName("가입")
    class SignUp {

        @Test
        @DisplayName("이름·이메일·닉네임·비밀번호 해시로 가입하면 인증 대기 계정이 된다 — 아직 저장 전이라 id가 없다")
        void createsPendingAccount() {
            StaffAccount account = newlySignedUpAccount();

            assertThat(account.id()).isEmpty();
            assertThat(account.status()).isEqualTo(StaffAccountStatus.PENDING_VERIFICATION);
            assertThat(account.personalInformation()).isEqualTo(personalInformation());
            assertThat(account.nickname()).isEqualTo(Nickname.of("조용한고래"));
            assertThat(account.passwordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(account.signedUpAt()).isEqualTo(SIGNUP_TIME);
            assertThat(account.isEmailVerified()).isFalse();
            assertThat(account.emailVerifiedAt()).isEmpty();
        }

        @Test
        @DisplayName("인증 전에는 로그인할 수 없다")
        void pendingAccountCannotLogIn() {
            assertThat(newlySignedUpAccount().canLogIn()).isFalse();
        }

        @Test
        @DisplayName("같은 이메일의 운영진 로그인이 있으면 '이미 가입된 이메일입니다'")
        void rejectsRegisteredEmail() {
            assertRejected(() -> StaffAccount.signUp(personalInformation(), NICKNAME, PASSWORD_HASH,
                    EmailAvailability.ALREADY_REGISTERED, NicknameAvailability.AVAILABLE, SIGNUP_TIME),
                    AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED);
            assertThat(AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED.message()).isEqualTo("이미 가입된 이메일입니다.");
        }

        @Test
        @DisplayName("다른 운영진이 쓰는 닉네임이면 '이미 사용 중인 닉네임입니다' — 중복 불가 (docs/99 C)")
        void rejectsTakenNickname() {
            assertRejected(() -> StaffAccount.signUp(personalInformation(), NICKNAME, PASSWORD_HASH,
                    EmailAvailability.AVAILABLE, NicknameAvailability.ALREADY_TAKEN, SIGNUP_TIME),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
            assertThat(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN.message()).isEqualTo("이미 사용 중인 닉네임입니다.");
        }

        @Test
        @DisplayName("이메일과 닉네임이 모두 겹치면 이메일로 막는다 — 이메일을 먼저 본다")
        void checksEmailBeforeNickname() {
            assertRejected(() -> StaffAccount.signUp(personalInformation(), NICKNAME, PASSWORD_HASH,
                    EmailAvailability.ALREADY_REGISTERED, NicknameAvailability.ALREADY_TAKEN, SIGNUP_TIME),
                    AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED);
        }
    }

    @Nested
    @DisplayName("이메일 인증")
    class VerifyEmail {

        @Test
        @DisplayName("인증 대기 계정이 인증을 마치면 활동 상태가 되고 로그인할 수 있다")
        void pendingBecomesActive() {
            StaffAccount account = newlySignedUpAccount();
            Instant verifiedAt = SIGNUP_TIME.plusSeconds(600);

            account.verifyEmail(verifiedAt);

            assertThat(account.status()).isEqualTo(StaffAccountStatus.ACTIVE);
            assertThat(account.emailVerifiedAt()).contains(verifiedAt);
            assertThat(account.canLogIn()).isTrue();
        }

        @Test
        @DisplayName("이미 인증한 계정은 '이미 이메일 인증을 마친 계정입니다'")
        void rejectsAlreadyVerified() {
            StaffAccount account = storedActiveAccount(1);

            assertRejected(() -> account.verifyEmail(SIGNUP_TIME.plusSeconds(900)), AccountErrorCode.EMAIL_ALREADY_VERIFIED);
        }

        @Test
        @DisplayName("탈퇴한 계정은 인증할 수 없다")
        void rejectsWithdrawn() {
            StaffAccount account = storedWithdrawnAccount(1);

            assertRejected(() -> account.verifyEmail(SIGNUP_TIME.plusSeconds(900)), AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("로그인할 수 있는지")
    class CanLogIn {

        @Test
        @DisplayName("활동 중인 계정만 된다 — 인증 대기·탈퇴는 안 된다")
        void onlyActive() {
            assertThat(storedActiveAccount(1).canLogIn()).isTrue();
            assertThat(storedPendingAccount(1).canLogIn()).isFalse();
            assertThat(storedWithdrawnAccount(1).canLogIn()).isFalse();
        }
    }

    @Nested
    @DisplayName("닉네임 변경 — 변경 가능 (docs/99 C)")
    class ChangeNickname {

        @Test
        @DisplayName("아무도 쓰지 않는 닉네임으로 바꾼다")
        void changesToAvailableNickname() {
            StaffAccount account = storedActiveAccount(1);

            boolean changed = account.changeNickname(Nickname.of("푸른바람"), NicknameAvailability.AVAILABLE);

            assertThat(changed).isTrue();
            assertThat(account.nickname()).isEqualTo(Nickname.of("푸른바람"));
        }

        @Test
        @DisplayName("다른 운영진이 쓰는 닉네임으로는 바꿀 수 없다 — 닉네임은 그대로다")
        void rejectsTakenNickname() {
            StaffAccount account = storedActiveAccount(1);

            assertRejected(() -> account.changeNickname(Nickname.of("푸른바람"), NicknameAvailability.ALREADY_TAKEN),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
            assertThat(account.nickname()).isEqualTo(NICKNAME);
        }

        @Test
        @DisplayName("지금 닉네임과 같으면 아무것도 바꾸지 않는다 — 저장소가 '쓰는 중'이라 답해도 이 계정이 쥔 것이라 막지 않는다")
        void sameNicknameIsNoChange() {
            StaffAccount account = storedActiveAccount(1);

            boolean changed = account.changeNickname(Nickname.of(" 조용한고래 "), NicknameAvailability.ALREADY_TAKEN);

            assertThat(changed).isFalse();
            assertThat(account.nickname()).isEqualTo(NICKNAME);
        }

        @Test
        @DisplayName("인증 대기 계정은 바꿀 수 없다")
        void rejectsPending() {
            StaffAccount account = storedPendingAccount(1);

            assertRejected(() -> account.changeNickname(Nickname.of("푸른바람"), NicknameAvailability.AVAILABLE),
                    AccountErrorCode.STAFF_NOT_ACTIVE);
        }

        @Test
        @DisplayName("탈퇴한 계정은 바꿀 수 없다")
        void rejectsWithdrawn() {
            StaffAccount account = storedWithdrawnAccount(1);

            assertRejected(() -> account.changeNickname(Nickname.of("푸른바람"), NicknameAvailability.AVAILABLE),
                    AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("한 줄 소개 변경 — 내 프로필 (docs/13)")
    class ChangeIntroduction {

        @Test
        @DisplayName("가입할 때는 소개가 없다 — 가입 칸에 받지 않는다 (docs/01 \"수집 정보\")")
        void newAccountHasNoIntroduction() {
            assertThat(newlySignedUpAccount().introduction()).isEmpty();
            assertThat(storedActiveAccount(1).introduction()).isEmpty();
        }

        @Test
        @DisplayName("소개를 적는다")
        void writesIntroduction() {
            StaffAccount account = storedActiveAccount(1);

            boolean changed = account.changeIntroduction(INTRODUCTION);

            assertThat(changed).isTrue();
            assertThat(account.introduction()).contains(new StaffIntroduction("극단에서 연출하고 있습니다"));
        }

        @Test
        @DisplayName("소개는 선택이라 지울 수 있다")
        void removesIntroduction() {
            StaffAccount account = storedActiveAccountWithIntroduction(1);

            boolean changed = account.changeIntroduction(null);

            assertThat(changed).isTrue();
            assertThat(account.introduction()).isEmpty();
        }

        @Test
        @DisplayName("지금 소개와 같으면 아무것도 바꾸지 않는다 — 앞뒤 공백만 다른 소개도 같은 소개다")
        void sameIntroductionIsNoChange() {
            StaffAccount withIntroduction = storedActiveAccountWithIntroduction(1);
            StaffAccount withoutIntroduction = storedActiveAccount(2);

            assertThat(withIntroduction.changeIntroduction(new StaffIntroduction(" 극단에서 연출하고 있습니다 "))).isFalse();
            assertThat(withoutIntroduction.changeIntroduction(null)).isFalse();
            assertThat(withIntroduction.introduction()).contains(INTRODUCTION);
        }

        @Test
        @DisplayName("인증 대기 계정과 탈퇴한 계정은 바꿀 수 없다")
        void rejectsInactive() {
            assertRejected(() -> storedPendingAccount(1).changeIntroduction(INTRODUCTION), AccountErrorCode.STAFF_NOT_ACTIVE);
            assertRejected(() -> storedWithdrawnAccount(2).changeIntroduction(INTRODUCTION), AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("내 프로필을 보거나 바꿀 수 있는지")
    class RequireActive {

        @Test
        @DisplayName("활동 중인 계정만 된다 — 인증 대기 · 탈퇴는 막는다")
        void activeOnly() {
            storedActiveAccount(1).requireActive();
            assertRejected(() -> storedPendingAccount(2).requireActive(), AccountErrorCode.STAFF_NOT_ACTIVE);
            assertRejected(() -> storedWithdrawnAccount(3).requireActive(), AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("첫 글자 표시 — 사진 대신 닉네임 첫 글자 + 사람마다 정해진 색 (docs/01 \"운영진 프로필 — 표시\")")
    class Avatar {

        @Test
        @DisplayName("조용한고래(계정 9)는 '조'와 팔레트 1번 — 8가지 색 가운데 계정 id로 정해진다")
        void initialAndPaletteFromNicknameAndId() {
            assertThat(storedActiveAccount(9).initialAvatar()).isEqualTo(new InitialAvatar("조", 1));
        }

        @Test
        @DisplayName("닉네임을 바꿔도 색은 그대로다 — 첫 글자만 바뀐다")
        void colorStaysWhenNicknameChanges() {
            StaffAccount account = storedActiveAccount(9);

            account.changeNickname(Nickname.of("푸른바람"), NicknameAvailability.AVAILABLE);

            assertThat(account.initialAvatar()).isEqualTo(new InitialAvatar("푸", 1));
        }

        @Test
        @DisplayName("저장 전 계정은 색을 정할 id가 없다")
        void requiresId() {
            assertThatIllegalStateException().isThrownBy(() -> newlySignedUpAccount().initialAvatar());
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("저장된 값 그대로 되살린다")
        void restoresStoredValues() {
            Instant verifiedAt = SIGNUP_TIME.plusSeconds(300);

            StaffAccount account = StaffAccount.restore(7, personalInformation(), NICKNAME, null, PASSWORD_HASH, SIGNUP_TIME,
                    StaffAccountStatus.ACTIVE, verifiedAt);

            assertThat(account.id()).hasValue(7);
            assertThat(account.nickname()).isEqualTo(NICKNAME);
            assertThat(account.emailVerifiedAt()).contains(verifiedAt);
            assertThat(account.introduction()).isEmpty();
            assertThat(storedActiveAccountWithIntroduction(8).introduction()).contains(INTRODUCTION);
        }

        @Test
        @DisplayName("인증 대기인데 인증 일시가 있거나, 활동인데 인증 일시가 없으면 받지 않는다")
        void rejectsStatusVerificationMismatch() {
            assertThatIllegalArgumentException().isThrownBy(() -> StaffAccount.restore(7, personalInformation(), NICKNAME,
                    null, PASSWORD_HASH, SIGNUP_TIME, StaffAccountStatus.PENDING_VERIFICATION, SIGNUP_TIME));
            assertThatIllegalArgumentException().isThrownBy(() -> StaffAccount.restore(7, personalInformation(), NICKNAME,
                    null, PASSWORD_HASH, SIGNUP_TIME, StaffAccountStatus.ACTIVE, null));
        }

        @Test
        @DisplayName("id는 1 이상이다")
        void rejectsNonPositiveId() {
            assertThatIllegalArgumentException().isThrownBy(() -> storedActiveAccount(0));
            assertThatIllegalArgumentException().isThrownBy(() -> storedWithdrawnAccount(0));
        }

        @Test
        @DisplayName("탈퇴한 계정은 개인정보와 함께 되살리지 않는다 — 탈퇴할 때 파기했으므로 닉네임 · 가입 일시 · 탈퇴 일시만으로 되살린다")
        void withdrawnAccountRestoresWithoutPersonalInformation() {
            assertThatIllegalArgumentException().isThrownBy(() -> StaffAccount.restore(7, personalInformation(), NICKNAME,
                    null, PASSWORD_HASH, SIGNUP_TIME, StaffAccountStatus.WITHDRAWN, null));

            Instant withdrawnAt = SIGNUP_TIME.plusSeconds(86_400);
            StaffAccount account = StaffAccount.restoreWithdrawn(7, NICKNAME, SIGNUP_TIME, withdrawnAt);

            assertThat(account.id()).hasValue(7);
            assertThat(account.status()).isEqualTo(StaffAccountStatus.WITHDRAWN);
            assertThat(account.nickname()).isEqualTo(NICKNAME);
            assertThat(account.signedUpAt()).isEqualTo(SIGNUP_TIME);
            assertThat(account.withdrawnAt()).contains(withdrawnAt);
            assertThat(account.emailVerifiedAt()).isEmpty();
            assertThat(account.canLogIn()).isFalse();
        }
    }

    @Nested
    @DisplayName("탈퇴")
    class Withdraw {

        private static final Instant WITHDRAWN_AT = SIGNUP_TIME.plusSeconds(30 * 86_400);

        @Test
        @DisplayName("활동 중인 계정이 탈퇴하면 이름 · 이메일 · 한 줄 소개 · 비밀번호 해시가 파기되고 닉네임 · 가입 일시만 남으며 로그인할 수 없다")
        void destroysPersonalInformationAndKeepsNickname() {
            StaffAccount account = storedActiveAccountWithIntroduction(1);

            account.withdraw(WITHDRAWN_AT);

            assertThat(account.status()).isEqualTo(StaffAccountStatus.WITHDRAWN);
            assertThat(account.isWithdrawn()).isTrue();
            assertThat(account.withdrawnAt()).contains(WITHDRAWN_AT);
            assertThat(account.nickname()).isEqualTo(NICKNAME);
            assertThat(account.signedUpAt()).isEqualTo(SIGNUP_TIME);
            assertThat(account.emailVerifiedAt()).isEmpty();
            assertThat(account.introduction()).isEmpty();
            assertThat(account.canLogIn()).isFalse();
            assertThat(account.canResetPassword()).isFalse();
            assertThatIllegalStateException().isThrownBy(account::personalInformation);
            assertThatIllegalStateException().isThrownBy(account::passwordHash);
        }

        @Test
        @DisplayName("이미 탈퇴한 계정은 다시 탈퇴하지 못한다")
        void rejectsWithdrawnAgain() {
            StaffAccount account = storedActiveAccount(1);
            account.withdraw(WITHDRAWN_AT);

            assertRejected(() -> account.withdraw(WITHDRAWN_AT.plusSeconds(60)), AccountErrorCode.STAFF_WITHDRAWN);
            assertRejected(() -> storedWithdrawnAccount(2).withdraw(WITHDRAWN_AT), AccountErrorCode.STAFF_WITHDRAWN);
            assertThat(account.withdrawnAt()).contains(WITHDRAWN_AT);
        }

        @Test
        @DisplayName("인증 대기 계정은 탈퇴하지 못한다 — 로그인할 수 없는 계정이라 탈퇴를 요청할 길도 없다")
        void rejectsPending() {
            StaffAccount account = storedPendingAccount(1);

            assertRejected(() -> account.withdraw(WITHDRAWN_AT), AccountErrorCode.STAFF_NOT_ACTIVE);
            assertThat(account.status()).isEqualTo(StaffAccountStatus.PENDING_VERIFICATION);
            assertThat(account.personalInformation()).isEqualTo(personalInformation());
        }

        @Test
        @DisplayName("탈퇴한 뒤에는 닉네임 · 소개 변경, 비밀번호 재설정, 이메일 인증을 하지 못한다")
        void withdrawnAccountCannotChange() {
            StaffAccount account = storedActiveAccount(1);
            account.withdraw(WITHDRAWN_AT);

            assertRejected(() -> account.changeNickname(Nickname.of("푸른바람"), NicknameAvailability.AVAILABLE),
                    AccountErrorCode.STAFF_WITHDRAWN);
            assertRejected(() -> account.changeIntroduction(INTRODUCTION), AccountErrorCode.STAFF_WITHDRAWN);
            assertRejected(() -> account.resetPassword(PASSWORD_HASH), AccountErrorCode.STAFF_WITHDRAWN);
            assertRejected(() -> account.verifyEmail(WITHDRAWN_AT), AccountErrorCode.STAFF_WITHDRAWN);
            assertThat(account.nickname()).isEqualTo(NICKNAME);
        }
    }

    @Nested
    @DisplayName("가입 정보")
    class PersonalInformation {

        @Test
        @DisplayName("이름은 앞뒤 공백을 걷고, 비어 있으면 받지 않는다")
        void nameIsRequired() {
            assertThat(StaffPersonalInformation.of(" 박정훈 ", "junghoon@example.com").name()).isEqualTo("박정훈");
            assertThatIllegalArgumentException().isThrownBy(() -> StaffPersonalInformation.of("  ", "junghoon@example.com"));
        }

        @Test
        @DisplayName("이메일은 앞뒤 공백을 걷고 형식을 본다")
        void emailIsChecked() {
            assertThat(StaffPersonalInformation.of("박정훈", " Junghoon@Example.com ").email())
                    .isEqualTo(EmailAddress.of("Junghoon@Example.com"));
            assertThatIllegalArgumentException().isThrownBy(() -> StaffPersonalInformation.of("박정훈", "junghoon"));
        }
    }
}
