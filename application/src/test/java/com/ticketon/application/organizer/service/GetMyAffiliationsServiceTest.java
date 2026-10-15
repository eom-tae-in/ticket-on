package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.application.organizer.port.in.GetMyAffiliationsQuery;
import com.ticketon.application.organizer.port.in.MyAffiliationsResult;
import com.ticketon.application.organizer.query.CurrentAffiliation;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 두 동아리를 돕는 박정훈 — 극단 열두달의 소유자이고 늦여름의 운영자다(docs/01 "한 사람이 여러 주최자에 소속될 수 있습니다"). */
@DisplayName("내 소속 목록 유스케이스 — 주최자 전환")
class GetMyAffiliationsServiceTest {

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));

    private static final Instant JOINED_AT = KoreanTime.toInstant(LocalDateTime.of(2023, 5, 1, 10, 0));

    private final FakeOrganizerHomeQueryPort homeQueryPort = new FakeOrganizerHomeQueryPort();
    private final GetMyAffiliationsService service = new GetMyAffiliationsService(homeQueryPort);

    @Test
    @DisplayName("지금 소속된 주최자마다 주최자명 · 역할 · 주최자 상태 · 소속 일시를 소속을 시작한 순서로 돌려준다")
    void listsCurrentAffiliations() {
        homeQueryPort.addAffiliation(JUNGHOON.id(),
                new CurrentAffiliation(12, "극단 열두달", StaffRole.OWNER, APPROVED_AT, OrganizerStatus.ACTIVE));
        homeQueryPort.addAffiliation(JUNGHOON.id(),
                new CurrentAffiliation(30, "늦여름", StaffRole.OPERATOR, JOINED_AT, OrganizerStatus.ACTIVE));
        homeQueryPort.addAffiliation(4,
                new CurrentAffiliation(30, "늦여름", StaffRole.OWNER, APPROVED_AT, OrganizerStatus.ACTIVE));

        MyAffiliationsResult result = service.getMyAffiliations(new GetMyAffiliationsQuery(JUNGHOON));

        assertThat(result.affiliations()).containsExactly(
                new MyAffiliationsResult.Affiliation(12, "극단 열두달", StaffRole.OWNER, OrganizerStatus.ACTIVE, APPROVED_AT),
                new MyAffiliationsResult.Affiliation(30, "늦여름", StaffRole.OPERATOR, OrganizerStatus.ACTIVE, JOINED_AT));
    }

    @Test
    @DisplayName("소속이 없으면 빈 목록이다")
    void emptyWithoutAffiliation() {
        assertThat(service.getMyAffiliations(new GetMyAffiliationsQuery(JUNGHOON)).affiliations()).isEmpty();
    }

    @Test
    @DisplayName("운영진이 아닌 주체로는 묻지 않는다")
    void staffOnly() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new GetMyAffiliationsQuery(AccountRef.of(AccountKind.AUDIENCE, 1)));
    }
}
