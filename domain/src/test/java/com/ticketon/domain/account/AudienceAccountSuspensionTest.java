package com.ticketon.domain.account;

import static com.ticketon.domain.account.AudienceAccountFixtures.PASSWORD_HASH;
import static com.ticketon.domain.account.AudienceAccountFixtures.SIGNUP_TIME;
import static com.ticketon.domain.account.AudienceAccountFixtures.activeAccount;
import static com.ticketon.domain.account.AudienceAccountFixtures.adultPersonalInformation;
import static com.ticketon.domain.account.AudienceAccountFixtures.assertRejected;
import static com.ticketon.domain.account.AudienceAccountFixtures.koreanTime;
import static com.ticketon.domain.account.AudienceAccountFixtures.newlySignedUpAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("관객 이용 정지")
class AudienceAccountSuspensionTest {

    private static final String REASON = "번호를 바꿔가며 1인 매수 제한을 넘는 예매";
    private static final Instant SUSPENDED_AT = koreanTime(2026, 11, 2, 14, 0);
    private static final Instant LIFTED_AT = koreanTime(2026, 12, 1, 9, 30);

    @Nested
    @DisplayName("정지하면")
    class WhenSuspended {

        @Test
        @DisplayName("이용 정지 상태가 되고 사유와 정지 시각이 남는다")
        void recordsReasonAndTime() {
            AudienceAccount account = suspendedAccount();

            assertThat(account.status()).isEqualTo(AudienceAccountStatus.SUSPENDED);
            assertThat(account.currentSuspension()).contains(new AudienceSuspension(REASON, SUSPENDED_AT, null));
        }

        @Test
        @DisplayName("예매는 막히고 취소는 된다")
        void bookingBlockedButCancellationAllowed() {
            AudienceAccount account = suspendedAccount();

            assertThat(account.can(AudienceAction.BOOK)).isFalse();
            assertThat(account.can(AudienceAction.CANCEL_TICKET)).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AudienceAction.class, names = {"BOOK", "CHANGE_BOOKING", "REPORT", "CHANGE_PHONE_NUMBER"})
        @DisplayName("막히는 것 — 새 예매·변경·신고 (docs/01), 정지를 피하는 길이 되는 전화번호 변경")
        void restrictedActions(AudienceAction action) {
            assertThat(suspendedAccount().can(action)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AudienceAction.class,
                names = {"LOG_IN", "VIEW_ACCOUNT", "VIEW_TICKETS", "SHOW_TICKET_QR", "CANCEL_TICKET", "RESET_PASSWORD",
                        "MANAGE_LOGIN_METHODS", "CHANGE_MARKETING_CONSENT"})
        @DisplayName("되는 것 — 로그인·계정 정보 보기·기존 티켓 보기·QR·취소·비밀번호 찾기·카카오·구글 연결 관리·"
                + "마케팅 수신 동의 변경(언제든 철회), 산 표는 뺏지 않는다 (docs/01)")
        void allowedActions(AudienceAction action) {
            assertThat(suspendedAccount().can(action)).isTrue();
        }

        @Test
        @DisplayName("이용 정지 중으로 보인다 — 활동 중인 계정과 인증 대기 계정은 아니다")
        void isSuspended() {
            assertThat(suspendedAccount().isSuspended()).isTrue();
            assertThat(activeAccount().isSuspended()).isFalse();
            assertThat(newlySignedUpAccount().isSuspended()).isFalse();
        }

        @Test
        @DisplayName("이미 정지된 계정은 다시 정지하지 않고, 처음 정지 기록이 그대로 남는다")
        void cannotSuspendTwice() {
            AudienceAccount account = suspendedAccount();

            assertRejected(() -> account.suspend("반복되는 허위 신고", SUSPENDED_AT.plusSeconds(60)),
                    AccountErrorCode.AUDIENCE_ALREADY_SUSPENDED);
            assertThat(account.suspensions()).containsExactly(new AudienceSuspension(REASON, SUSPENDED_AT, null));
        }
    }

    @Nested
    @DisplayName("해제하면")
    class WhenLifted {

        @Test
        @DisplayName("활동 상태로 돌아가 다시 예매할 수 있다")
        void canBookAgain() {
            AudienceAccount account = suspendedAccount();

            account.lift(LIFTED_AT);

            assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(account.can(AudienceAction.BOOK)).isTrue();
            assertThat(account.currentSuspension()).isEmpty();
            assertThat(account.isSuspended()).isFalse();
        }

        @Test
        @DisplayName("정지 이력은 지우지 않고 해제 시각을 더해 남긴다")
        void keepsHistoryWithLiftTime() {
            AudienceAccount account = suspendedAccount();

            account.lift(LIFTED_AT);

            assertThat(account.suspensions()).containsExactly(new AudienceSuspension(REASON, SUSPENDED_AT, LIFTED_AT));
        }

        @Test
        @DisplayName("해제 뒤 다시 정지하면 이력이 둘이 된다")
        void suspendAgainAfterLift() {
            AudienceAccount account = suspendedAccount();
            account.lift(LIFTED_AT);
            Instant suspendedAgainAt = LIFTED_AT.plusSeconds(86_400);

            account.suspend("반복되는 허위 신고", suspendedAgainAt);

            assertThat(account.suspensions()).containsExactly(
                    new AudienceSuspension(REASON, SUSPENDED_AT, LIFTED_AT),
                    new AudienceSuspension("반복되는 허위 신고", suspendedAgainAt, null));
            assertThat(account.status()).isEqualTo(AudienceAccountStatus.SUSPENDED);
        }

        @Test
        @DisplayName("정지되지 않은 계정은 해제할 수 없다")
        void cannotLiftWhenNotSuspended() {
            assertRejected(() -> activeAccount().lift(LIFTED_AT), AccountErrorCode.AUDIENCE_NOT_SUSPENDED);
            assertRejected(() -> newlySignedUpAccount().lift(LIFTED_AT), AccountErrorCode.AUDIENCE_NOT_SUSPENDED);
        }

        @Test
        @DisplayName("해제 시각이 정지 시각보다 앞서면 거부하고 정지 상태를 유지한다")
        void rejectsLiftBeforeSuspension() {
            AudienceAccount account = suspendedAccount();

            assertThatThrownBy(() -> account.lift(SUSPENDED_AT.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
            assertThat(account.status()).isEqualTo(AudienceAccountStatus.SUSPENDED);
        }
    }

    @Nested
    @DisplayName("정지할 수 있는 계정")
    class SuspensionTargets {

        @Test
        @DisplayName("사유가 비면 정지하지 않는다")
        void reasonIsRequired() {
            AudienceAccount account = activeAccount();

            assertThatThrownBy(() -> account.suspend("  ", SUSPENDED_AT)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> account.suspend(null, SUSPENDED_AT)).isInstanceOf(NullPointerException.class);
            assertThat(account.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(account.suspensions()).isEmpty();
        }

        @Test
        @DisplayName("이메일 인증 전(인증 대기) 계정은 정지하지 않는다")
        void pendingAccountCannotBeSuspended() {
            assertRejected(() -> newlySignedUpAccount().suspend(REASON, SUSPENDED_AT), AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        }

        @Test
        @DisplayName("탈퇴한 계정은 정지도 해제도 하지 않는다")
        void withdrawnAccountCannotBeSuspendedOrLifted() {
            AudienceAccount withdrawn = restored(AudienceAccountStatus.WITHDRAWN,
                    List.of(new AudienceSuspension(REASON, SUSPENDED_AT, null)));

            assertRejected(() -> withdrawn.suspend(REASON, LIFTED_AT), AccountErrorCode.AUDIENCE_WITHDRAWN);
            assertRejected(() -> withdrawn.lift(LIFTED_AT), AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("상태별로 할 수 있는 일")
    class ActionsByStatus {

        @ParameterizedTest(name = "{0}")
        @EnumSource(AudienceAction.class)
        @DisplayName("활동 중이면 전부 된다")
        void activeCanDoEverything(AudienceAction action) {
            assertThat(activeAccount().can(action)).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(AudienceAction.class)
        @DisplayName("인증 대기와 탈퇴는 로그인부터 할 수 없어 아무것도 못 한다")
        void pendingAndWithdrawnCanDoNothing(AudienceAction action) {
            assertThat(newlySignedUpAccount().can(action)).isFalse();
            assertThat(restored(AudienceAccountStatus.WITHDRAWN, List.of()).can(action)).isFalse();
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("정지 중인 계정을 되살려 해제할 수 있다")
        void restoresSuspendedAccount() {
            AudienceAccount account = restored(AudienceAccountStatus.SUSPENDED,
                    List.of(new AudienceSuspension(REASON, SUSPENDED_AT, null)));

            account.lift(LIFTED_AT);

            assertThat(account.suspensions()).containsExactly(new AudienceSuspension(REASON, SUSPENDED_AT, LIFTED_AT));
        }

        @Test
        @DisplayName("정지 상태와 풀리지 않은 정지가 맞지 않으면 거부한다")
        void rejectsStatusSuspensionMismatch() {
            AudienceSuspension unlifted = new AudienceSuspension(REASON, SUSPENDED_AT, null);
            AudienceSuspension lifted = new AudienceSuspension(REASON, SUSPENDED_AT, LIFTED_AT);

            assertThatThrownBy(() -> restored(AudienceAccountStatus.SUSPENDED, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> restored(AudienceAccountStatus.SUSPENDED, List.of(lifted)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> restored(AudienceAccountStatus.ACTIVE, List.of(unlifted)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("풀리지 않은 정지는 이력의 마지막 하나뿐이다")
        void onlyLastSuspensionMayBeUnlifted() {
            AudienceSuspension unlifted = new AudienceSuspension(REASON, SUSPENDED_AT, null);
            AudienceSuspension lifted = new AudienceSuspension(REASON, SUSPENDED_AT, LIFTED_AT);

            assertThatThrownBy(() -> restored(AudienceAccountStatus.SUSPENDED, List.of(unlifted, unlifted)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> restored(AudienceAccountStatus.ACTIVE, List.of(unlifted, lifted)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("정지 기록 한 줄")
    class SuspensionRecord {

        @Test
        @DisplayName("사유의 앞뒤 공백을 걷는다")
        void stripsReason() {
            assertThat(new AudienceSuspension("  " + REASON + " ", SUSPENDED_AT, null).reason()).isEqualTo(REASON);
        }

        @Test
        @DisplayName("해제 시각이 정지 시각보다 앞설 수 없다")
        void liftCannotPrecedeSuspension() {
            assertThatThrownBy(() -> new AudienceSuspension(REASON, SUSPENDED_AT, SUSPENDED_AT.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new AudienceSuspension(REASON, SUSPENDED_AT, SUSPENDED_AT).isLifted()).isTrue();
        }
    }

    private static AudienceAccount suspendedAccount() {
        AudienceAccount account = activeAccount();
        account.suspend(REASON, SUSPENDED_AT);
        return account;
    }

    private static AudienceAccount restored(AudienceAccountStatus status, List<AudienceSuspension> suspensions) {
        return AudienceAccount.restore(7, adultPersonalInformation(), PASSWORD_HASH, SIGNUP_TIME,
                SignupConsentChoices.agreeingToAllRequired(false).recordsAt(SIGNUP_TIME), status,
                SIGNUP_TIME.plusSeconds(300), suspensions);
    }
}
