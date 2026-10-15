package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.OptionalLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(docs/15 박정훈)의 운영자 초대 — 대표 박정훈이 소유자, 기획 단원이 운영자. 박정훈이 이도현(늦여름의 운영자, docs/15)을 이메일로
 * 초대한다. 이도현은 열두달에는 바깥 사람이다.
 */
@DisplayName("운영자 초대 (docs/01 \"운영자 초대\")")
class StaffInvitationTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;
    private static final long DOHYUN = 4L;

    private static final EmailAddress DOHYUN_EMAIL = EmailAddress.of("dohyun@example.com");

    private static final Instant APPROVED_AT = at(2019, 3, 4, 10, 0);
    private static final Instant PLANNER_JOINED_AT = at(2019, 5, 1, 10, 0);

    /** 초대한 일시 — 2026-10-15 10:00 (한국 시각). */
    private static final Instant INVITED_AT = at(2026, 10, 15, 10, 0);

    /** 유효 기간(7일, 확인 필요 #25)이 끝나는 일시 — 2026-10-22 10:00. 이 순간부터 답할 수 없다. */
    private static final Instant EXPIRES_AT = at(2026, 10, 22, 10, 0);

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    /** 승인 뒤 기획 단원이 운영자로 들어온 열두달. */
    private static OrganizerStaff twelveMonthsStaff() {
        OrganizerStaff staff = OrganizerStaff.startWithOwner(TWELVE_MONTHS, JUNGHOON, APPROVED_AT);
        staff.addOperator(PLANNER, PLANNER_JOINED_AT);
        return staff;
    }

    private static Organizer twelveMonths(OrganizerStatus status) {
        return Organizer.restore(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), status, OrganizerPublicProfile.empty());
    }

    /** 박정훈이 이도현의 이메일로 보낸 초대 — 이도현은 이미 운영진 로그인이 있다. */
    private static StaffInvitation invitationToDohyun(OrganizerStaff staff) {
        return StaffInvitation.issue(staff, JUNGHOON, DOHYUN_EMAIL, OptionalLong.of(DOHYUN), INVITED_AT);
    }

    private static void assertPermissionDenied(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Nested
    @DisplayName("보내기")
    class Issue {

        @Test
        @DisplayName("소유자가 이메일로 보내면 답을 기다리는 초대가 되고, 유효 기간은 보낸 때부터 7일이다 (확인 필요 #25)")
        void ownerInvitesByEmail() {
            StaffInvitation invitation = invitationToDohyun(twelveMonthsStaff());

            assertThat(ProposedPolicy.STAFF_INVITATION_VALIDITY).isEqualTo(Duration.ofDays(7));
            assertThat(invitation.id()).isEmpty();
            assertThat(invitation.organizerId()).isEqualTo(TWELVE_MONTHS);
            assertThat(invitation.inviteeEmail()).isEqualTo(DOHYUN_EMAIL);
            assertThat(invitation.invitedByStaffAccountId()).isEqualTo(JUNGHOON);
            assertThat(invitation.invitedAt()).isEqualTo(INVITED_AT);
            assertThat(invitation.expiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
            assertThat(invitation.answeredAt()).isEmpty();
            assertThat(invitation.answeredByStaffAccountId()).isEmpty();
        }

        @Test
        @DisplayName("아직 주최자 쪽 로그인이 없는 이메일에도 보낸다 — 받은 사람이 그 이메일로 로그인을 만들어 답한다")
        void invitesEmailWithoutStaffLogin() {
            StaffInvitation invitation = StaffInvitation.issue(twelveMonthsStaff(), JUNGHOON,
                    EmailAddress.of("new-member@example.com"), OptionalLong.empty(), INVITED_AT);

            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("운영자는 초대하지 못한다 — 운영진 초대는 소유자만 (403 OWNER_ONLY_ACTION)")
        void operatorCannotInvite() {
            assertPermissionDenied(() -> StaffInvitation.issue(twelveMonthsStaff(), PLANNER, DOHYUN_EMAIL,
                    OptionalLong.of(DOHYUN), INVITED_AT), OrganizerErrorCode.OWNER_ONLY_ACTION);
        }

        @Test
        @DisplayName("그 주최자의 운영진이 아닌 사람은 초대하지 못한다 (403 NOT_ORGANIZER_STAFF)")
        void outsiderCannotInvite() {
            assertPermissionDenied(() -> StaffInvitation.issue(twelveMonthsStaff(), DOHYUN, DOHYUN_EMAIL,
                    OptionalLong.of(DOHYUN), INVITED_AT), OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("이미 그 주최자의 운영진인 사람(운영자 · 소유자 자신)에게는 보내지 않는다")
        void rejectsCurrentStaff() {
            assertRejected(() -> StaffInvitation.issue(twelveMonthsStaff(), JUNGHOON,
                    EmailAddress.of("planner@example.com"), OptionalLong.of(PLANNER), INVITED_AT),
                    OrganizerErrorCode.ALREADY_ORGANIZER_STAFF);
            assertRejected(() -> StaffInvitation.issue(twelveMonthsStaff(), JUNGHOON,
                    EmailAddress.of("junghoon@example.com"), OptionalLong.of(JUNGHOON), INVITED_AT),
                    OrganizerErrorCode.ALREADY_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("전에 나간 사람에게는 다시 보낸다 — 지난 소속은 이력일 뿐이다")
        void invitesFormerMember() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.leave(PLANNER, INVITED_AT.minus(Duration.ofDays(30)));

            StaffInvitation invitation = StaffInvitation.issue(staff, JUNGHOON, EmailAddress.of("planner@example.com"),
                    OptionalLong.of(PLANNER), INVITED_AT);

            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("유효 기간과 받는 사람")
    class ValidityAndAddressee {

        @Test
        @DisplayName("7일에서 1초 모자라면 아직 유효하고, 7일이 되는 순간부터 지난 것이다")
        void expiresExactlyAfterSevenDays() {
            StaffInvitation invitation = invitationToDohyun(twelveMonthsStaff());

            assertThat(invitation.isExpiredAt(EXPIRES_AT.minusSeconds(1))).isFalse();
            assertThat(invitation.isExpiredAt(EXPIRES_AT)).isTrue();
        }

        @Test
        @DisplayName("받는 사람은 이메일로 가리고, 대소문자는 가리지 않는다")
        void addresseeIsEmailIgnoringCase() {
            StaffInvitation invitation = invitationToDohyun(twelveMonthsStaff());

            assertThat(invitation.isAddressedTo(EmailAddress.of("dohyun@example.com"))).isTrue();
            assertThat(invitation.isAddressedTo(EmailAddress.of("DoHyun@Example.COM"))).isTrue();
            assertThat(invitation.isAddressedTo(EmailAddress.of("dohyun@example.org"))).isFalse();
        }
    }

    @Nested
    @DisplayName("수락")
    class Accept {

        @Test
        @DisplayName("유효 기간 안에 초대받은 이메일로 수락하면 바로 운영자 소속이 시작되고 수락으로 남는다 — 심사를 받지 않는다")
        void acceptingStartsOperatorMembership() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);
            Instant acceptedAt = at(2026, 10, 16, 21, 0);

            StaffMembership joined = invitation.accept(DOHYUN, EmailAddress.of("DOHYUN@example.com"),
                    twelveMonths(OrganizerStatus.ACTIVE), staff, acceptedAt);

            assertThat(joined).isEqualTo(new StaffMembership(TWELVE_MONTHS, DOHYUN, StaffRole.OPERATOR, acceptedAt, null));
            assertThat(staff.roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
            assertThat(invitation.answeredAt()).contains(acceptedAt);
            assertThat(invitation.answeredByStaffAccountId()).hasValue(DOHYUN);
        }

        @Test
        @DisplayName("다른 주최자(늦여름)의 운영자도 수락해 열두달의 운영자가 된다 — 한 사람이 여러 주최자에 소속된다")
        void staffOfAnotherOrganizerCanJoin() {
            OrganizerStaff lateSummerStaff = OrganizerStaff.startWithOwner(30L, 5L, APPROVED_AT);
            lateSummerStaff.addOperator(DOHYUN, APPROVED_AT);
            OrganizerStaff staff = twelveMonthsStaff();

            invitationToDohyun(staff).accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.ACTIVE), staff,
                    INVITED_AT.plusSeconds(60));

            assertThat(staff.roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
            assertThat(lateSummerStaff.roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
        }

        @Test
        @DisplayName("나갔다 다시 초대받아 수락하면 새 소속이 시작되고 지난 소속은 이력으로 남는다")
        void rejoiningKeepsHistory() {
            OrganizerStaff staff = twelveMonthsStaff();
            Instant leftAt = INVITED_AT.minus(Duration.ofDays(30));
            staff.leave(PLANNER, leftAt);
            StaffInvitation invitation = StaffInvitation.issue(staff, JUNGHOON, EmailAddress.of("planner@example.com"),
                    OptionalLong.of(PLANNER), INVITED_AT);

            invitation.accept(PLANNER, EmailAddress.of("planner@example.com"), twelveMonths(OrganizerStatus.ACTIVE), staff,
                    INVITED_AT.plusSeconds(60));

            assertThat(staff.memberships()).filteredOn(membership -> membership.staffAccountId() == PLANNER)
                    .containsExactly(
                            new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, leftAt),
                            new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, INVITED_AT.plusSeconds(60),
                                    null));
        }

        @Test
        @DisplayName("유효 기간이 끝나는 순간부터는 수락하지 못하고, 명단도 초대도 그대로다")
        void rejectsExpiredInvitation() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);

            assertRejected(() -> invitation.accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.ACTIVE), staff,
                    EXPIRES_AT), OrganizerErrorCode.STAFF_INVITATION_EXPIRED);
            assertThat(staff.roleOf(DOHYUN)).isEmpty();
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("이미 수락하거나 거절한 초대는 다시 수락하지 못한다")
        void rejectsAnsweredInvitation() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation accepted = invitationToDohyun(staff);
            accepted.accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.ACTIVE), staff, INVITED_AT.plusSeconds(60));
            StaffInvitation declined = invitationToDohyun(twelveMonthsStaff());
            declined.decline(DOHYUN, DOHYUN_EMAIL, INVITED_AT.plusSeconds(60));

            assertRejected(() -> accepted.accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.ACTIVE), staff,
                    INVITED_AT.plusSeconds(120)), OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED);
            assertRejected(() -> declined.accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.ACTIVE),
                    twelveMonthsStaff(), INVITED_AT.plusSeconds(120)), OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED);
        }

        @Test
        @DisplayName("그 사이 활동 중이 아니게 된 주최자(닫힘)에는 들어가지 않는다")
        void rejectsInactiveOrganizer() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);

            assertRejected(() -> invitation.accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.CLOSED), staff,
                    INVITED_AT.plusSeconds(60)), OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("그 사이 이미 그 주최자의 운영진이 됐으면 수락하지 않고 초대도 그대로다")
        void rejectsWhenAlreadyStaff() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);
            staff.addOperator(DOHYUN, INVITED_AT.plusSeconds(30));

            assertRejected(() -> invitation.accept(DOHYUN, DOHYUN_EMAIL, twelveMonths(OrganizerStatus.ACTIVE), staff,
                    INVITED_AT.plusSeconds(60)), OrganizerErrorCode.ALREADY_ORGANIZER_STAFF);
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }

        @Test
        @DisplayName("초대받은 이메일이 아닌 운영진 로그인으로는 수락하지 못한다 — 부르는 쪽이 먼저 거르는 잘못이다")
        void rejectsAnotherEmail() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);

            assertThatIllegalArgumentException().isThrownBy(() -> invitation.accept(DOHYUN,
                    EmailAddress.of("someone-else@example.com"), twelveMonths(OrganizerStatus.ACTIVE), staff,
                    INVITED_AT.plusSeconds(60)));
            assertThat(staff.roleOf(DOHYUN)).isEmpty();
        }

        @Test
        @DisplayName("다른 주최자의 명단으로는 수락하지 않는다")
        void rejectsOtherOrganizerStaff() {
            StaffInvitation invitation = invitationToDohyun(twelveMonthsStaff());
            OrganizerStaff otherStaff = OrganizerStaff.startWithOwner(30L, 5L, APPROVED_AT);

            assertThatIllegalArgumentException().isThrownBy(() -> invitation.accept(DOHYUN, DOHYUN_EMAIL,
                    twelveMonths(OrganizerStatus.ACTIVE), otherStaff, INVITED_AT.plusSeconds(60)));
        }
    }

    @Nested
    @DisplayName("거절")
    class Decline {

        @Test
        @DisplayName("거절하면 거절로 남고 명단은 바뀌지 않는다")
        void decliningLeavesStaffUnchanged() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);
            Instant declinedAt = at(2026, 10, 16, 9, 0);

            invitation.decline(DOHYUN, DOHYUN_EMAIL, declinedAt);

            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.DECLINED);
            assertThat(invitation.answeredAt()).contains(declinedAt);
            assertThat(invitation.answeredByStaffAccountId()).hasValue(DOHYUN);
            assertThat(staff.roleOf(DOHYUN)).isEmpty();
        }

        @Test
        @DisplayName("유효 기간이 지났거나 이미 답한 초대는 거절하지 못한다")
        void rejectsExpiredOrAnswered() {
            StaffInvitation expired = invitationToDohyun(twelveMonthsStaff());
            StaffInvitation declined = invitationToDohyun(twelveMonthsStaff());
            declined.decline(DOHYUN, DOHYUN_EMAIL, INVITED_AT.plusSeconds(60));

            assertRejected(() -> expired.decline(DOHYUN, DOHYUN_EMAIL, EXPIRES_AT),
                    OrganizerErrorCode.STAFF_INVITATION_EXPIRED);
            assertRejected(() -> declined.decline(DOHYUN, DOHYUN_EMAIL, INVITED_AT.plusSeconds(120)),
                    OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED);
        }

        @Test
        @DisplayName("초대받은 이메일이 아니면 거절하지 못한다")
        void rejectsAnotherEmail() {
            StaffInvitation invitation = invitationToDohyun(twelveMonthsStaff());

            assertThatIllegalArgumentException().isThrownBy(() -> invitation.decline(DOHYUN,
                    EmailAddress.of("someone-else@example.com"), INVITED_AT.plusSeconds(60)));
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("같은 이메일로 다시 초대")
    class Reinvite {

        @Test
        @DisplayName("유효 기간이 지난 초대는 다시 초대하면 지금부터 7일 동안 다시 열리고, 보낸 사람·일시가 바뀐다")
        void reopensExpiredInvitation() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);
            Instant reinvitedAt = at(2026, 10, 25, 14, 0);

            invitation.reinvite(staff, JUNGHOON, OptionalLong.of(DOHYUN), reinvitedAt);

            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
            assertThat(invitation.invitedAt()).isEqualTo(reinvitedAt);
            assertThat(invitation.expiresAt()).isEqualTo(at(2026, 11, 1, 14, 0));
            assertThat(invitation.isExpiredAt(reinvitedAt.plusSeconds(60))).isFalse();
        }

        @Test
        @DisplayName("아직 유효한 초대가 있으면 다시 보내지 않는다 — 받은 사람이 이미 답할 수 있다")
        void rejectsWhileStillValid() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);

            assertRejected(() -> invitation.reinvite(staff, JUNGHOON, OptionalLong.of(DOHYUN), EXPIRES_AT.minusSeconds(1)),
                    OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING);
            assertThat(invitation.invitedAt()).isEqualTo(INVITED_AT);
        }

        @Test
        @DisplayName("운영자는 다시 초대하지 못하고, 그 사이 운영진이 된 사람에게는 다시 보내지 않는다")
        void appliesInvitingRules() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);

            assertPermissionDenied(() -> invitation.reinvite(staff, PLANNER, OptionalLong.of(DOHYUN), EXPIRES_AT),
                    OrganizerErrorCode.OWNER_ONLY_ACTION);
            staff.addOperator(DOHYUN, EXPIRES_AT);
            assertRejected(() -> invitation.reinvite(staff, JUNGHOON, OptionalLong.of(DOHYUN), EXPIRES_AT),
                    OrganizerErrorCode.ALREADY_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("이미 답한 초대는 다시 보내지 않는다 — 답을 기다리는 초대만 다시 연다")
        void rejectsAnsweredInvitation() {
            OrganizerStaff staff = twelveMonthsStaff();
            StaffInvitation invitation = invitationToDohyun(staff);
            invitation.decline(DOHYUN, DOHYUN_EMAIL, INVITED_AT.plusSeconds(60));

            assertThatIllegalStateException().isThrownBy(
                    () -> invitation.reinvite(staff, JUNGHOON, OptionalLong.of(DOHYUN), EXPIRES_AT));
        }
    }

    @Nested
    @DisplayName("되살리기")
    class Restore {

        @Test
        @DisplayName("저장된 값 그대로 되살린다")
        void restoresStoredValues() {
            Instant acceptedAt = INVITED_AT.plusSeconds(60);

            StaffInvitation invitation = StaffInvitation.restore(7L, TWELVE_MONTHS, DOHYUN_EMAIL, JUNGHOON, INVITED_AT,
                    EXPIRES_AT, StaffInvitationStatus.ACCEPTED, acceptedAt, DOHYUN);

            assertThat(invitation.id()).hasValue(7L);
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
            assertThat(invitation.answeredAt()).contains(acceptedAt);
            assertThat(invitation.answeredByStaffAccountId()).hasValue(DOHYUN);
        }

        @Test
        @DisplayName("상태와 답한 일시·사람이 맞지 않거나 유효 기간이 보낸 일시보다 앞서면 되살리지 않는다")
        void rejectsInconsistentValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> StaffInvitation.restore(7L, TWELVE_MONTHS, DOHYUN_EMAIL,
                    JUNGHOON, INVITED_AT, EXPIRES_AT, StaffInvitationStatus.PENDING, INVITED_AT, DOHYUN));
            assertThatIllegalArgumentException().isThrownBy(() -> StaffInvitation.restore(7L, TWELVE_MONTHS, DOHYUN_EMAIL,
                    JUNGHOON, INVITED_AT, EXPIRES_AT, StaffInvitationStatus.DECLINED, null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> StaffInvitation.restore(7L, TWELVE_MONTHS, DOHYUN_EMAIL,
                    JUNGHOON, INVITED_AT, EXPIRES_AT, StaffInvitationStatus.ACCEPTED, INVITED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> StaffInvitation.restore(7L, TWELVE_MONTHS, DOHYUN_EMAIL,
                    JUNGHOON, INVITED_AT, INVITED_AT, StaffInvitationStatus.PENDING, null, null));
        }
    }
}
