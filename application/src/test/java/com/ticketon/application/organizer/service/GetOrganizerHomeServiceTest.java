package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.organizer.port.in.GetOrganizerHomeQuery;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.Affiliation;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ApplicationUnderReview;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ReceivedInvitation;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.RejectedApplication;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ReopenableOrganizer;
import com.ticketon.application.organizer.query.CurrentAffiliation;
import com.ticketon.application.organizer.query.LatestOrganizerApplication;
import com.ticketon.application.organizer.query.ReceivedOrganizerInvitation;
import com.ticketon.application.organizer.query.ReopenableClosedOrganizer;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 내 주최 홈 상태 조회 유스케이스 — docs/13 "3. 주최 관리 — 로그인"의 갈래(소속 없음 · 심사 중 · 주최자 전환)를 고를 재료가 나오는지 본다.
 */
@DisplayName("내 주최 홈 상태 조회 유스케이스")
class GetOrganizerHomeServiceTest {

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    /** 홈을 여는 시각 — 2026-10-18 09:00 (한국 시각). */
    private static final Instant NOW = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 18, 9, 0));

    private final FakeOrganizerHomeQueryPort homeQueryPort = new FakeOrganizerHomeQueryPort();

    private final GetOrganizerHomeService service = new GetOrganizerHomeService(homeQueryPort, MutableClock.at(NOW));

    @Test
    @DisplayName("신청도 소속도 없으면 모두 비어 있다 — 화면은 '새 주최자 신청'과 '받은 초대'를 보인다")
    void nothingYet() {
        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home).isEqualTo(new OrganizerHomeResult(List.of(), null, null, List.of(), List.of()));
    }

    @Test
    @DisplayName("가장 최근 신청이 심사 중이면 심사 중인 신청으로 보이고, 소속은 없다")
    void latestUnderReview() {
        homeQueryPort.setLatestApplication(JUNGHOON.id(), new LatestOrganizerApplication(3, "극단 열두달",
                OrganizerApplicationStatus.UNDER_REVIEW, SUBMITTED_AT, null, null));

        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home.applicationUnderReview()).isEqualTo(new ApplicationUnderReview(3, "극단 열두달", SUBMITTED_AT));
        assertThat(home.rejectedApplication()).isNull();
        assertThat(home.affiliations()).isEmpty();
    }

    @Test
    @DisplayName("가장 최근 신청이 반려면 반려된 신청과 사유가 보인다 — 다시 신청할 수 있다")
    void latestRejected() {
        homeQueryPort.setLatestApplication(JUNGHOON.id(), new LatestOrganizerApplication(3, "극단 열두달",
                OrganizerApplicationStatus.REJECTED, SUBMITTED_AT, REVIEWED_AT, "연락이 닿지 않음"));

        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home.rejectedApplication()).isEqualTo(new RejectedApplication(3, "극단 열두달", SUBMITTED_AT,
                REVIEWED_AT, "연락이 닿지 않음"));
        assertThat(home.applicationUnderReview()).isNull();
    }

    @Test
    @DisplayName("가장 최근 신청이 승인이면 신청은 보이지 않고 소속에 나온다")
    void latestApproved() {
        homeQueryPort.setLatestApplication(JUNGHOON.id(), new LatestOrganizerApplication(3, "극단 열두달",
                OrganizerApplicationStatus.APPROVED, SUBMITTED_AT, REVIEWED_AT, null));
        homeQueryPort.addAffiliation(JUNGHOON.id(),
                new CurrentAffiliation(12, "극단 열두달", StaffRole.OWNER, REVIEWED_AT, OrganizerStatus.ACTIVE));

        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home.applicationUnderReview()).isNull();
        assertThat(home.rejectedApplication()).isNull();
        assertThat(home.affiliations()).containsExactly(new Affiliation(12, "극단 열두달", StaffRole.OWNER, REVIEWED_AT));
    }

    @Test
    @DisplayName("여러 주최자에 소속돼 있으면 모두 역할과 함께 나온다 — 주최자 전환")
    void severalAffiliations() {
        homeQueryPort.addAffiliation(JUNGHOON.id(),
                new CurrentAffiliation(12, "극단 열두달", StaffRole.OWNER, SUBMITTED_AT, OrganizerStatus.ACTIVE));
        homeQueryPort.addAffiliation(JUNGHOON.id(),
                new CurrentAffiliation(30, "늦여름", StaffRole.OPERATOR, REVIEWED_AT, OrganizerStatus.ACTIVE));

        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home.affiliations()).containsExactly(
                new Affiliation(12, "극단 열두달", StaffRole.OWNER, SUBMITTED_AT),
                new Affiliation(30, "늦여름", StaffRole.OPERATOR, REVIEWED_AT));
    }

    @Test
    @DisplayName("받은 초대와 다시 열 수 있는 닫힌 주최자를 조회 포트가 돌려준 그대로 담는다 — 받은 초대의 유효 기간은 지금 시각으로 본다")
    void invitationsAndReopenableOrganizers() {
        Instant expiresAt = SUBMITTED_AT.plus(Duration.ofDays(7));
        homeQueryPort.addInvitation(JUNGHOON.id(), new ReceivedOrganizerInvitation(51, 30, "늦여름", SUBMITTED_AT, expiresAt));
        homeQueryPort.addReopenableOrganizer(JUNGHOON.id(), new ReopenableClosedOrganizer(8, "극단 봄날"));

        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home.receivedInvitations())
                .containsExactly(new ReceivedInvitation(51, 30, "늦여름", SUBMITTED_AT, expiresAt));
        assertThat(home.reopenableOrganizers()).containsExactly(new ReopenableOrganizer(8, "극단 봄날"));
        assertThat(homeQueryPort.lastInvitationQueryTime()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다른 사람의 신청·소속은 섞이지 않는다")
    void onlyThatPerson() {
        homeQueryPort.setLatestApplication(4, new LatestOrganizerApplication(5, "늦여름",
                OrganizerApplicationStatus.UNDER_REVIEW, SUBMITTED_AT, null, null));
        homeQueryPort.addAffiliation(4,
                new CurrentAffiliation(30, "늦여름", StaffRole.OWNER, REVIEWED_AT, OrganizerStatus.ACTIVE));

        OrganizerHomeResult home = service.getHome(new GetOrganizerHomeQuery(JUNGHOON));

        assertThat(home.applicationUnderReview()).isNull();
        assertThat(home.affiliations()).isEmpty();
    }
}
