package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.GetOrganizerBusinessDetailsQuery;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult.BusinessInformation;
import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult.MaskedSettlementAccount;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주최 관리의 "주최자 정보 · 사업자 정보 · 정산 계좌" 화면(소유자만, docs/11 "구조"). 확인 전에는 기존 값과 확인 중인 요청을 함께 돌려준다
 * ({@link OrganizerStaffScene} — 열두달은 비사업자 · 국민은행 계좌, 늦여름은 사업자 · 신한은행 계좌).
 */
@DisplayName("사업자 정보 · 정산 계좌 보기 유스케이스")
class GetOrganizerBusinessDetailsServiceTest {

    private static final SettlementAccount WOORI = new SettlementAccount("우리은행", "1002-345-678901", "박정훈");

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final GetOrganizerBusinessDetailsService service = scene.getOrganizerBusinessDetailsService();

    private OrganizerBusinessDetailsResult get(long staffAccountId, long organizerId) {
        return service.get(new GetOrganizerBusinessDetailsQuery(acting(staffAccountId, organizerId)));
    }

    @Test
    @DisplayName("비사업자 주최자의 소유자는 사업자 여부와 계좌번호를 가린 정산 계좌를 본다 — 확인 중인 요청이 없다")
    void ownerSeesCurrentDetailsWithMaskedAccount() {
        OrganizerBusinessDetailsResult result = get(JUNGHOON, scene.twelveMonths);

        assertThat(result.organizerId()).isEqualTo(scene.twelveMonths);
        assertThat(result.businessStatus()).isEqualTo(BusinessStatus.NON_BUSINESS);
        assertThat(result.businessInformation()).isNull();
        assertThat(result.settlementAccount()).isEqualTo(new MaskedSettlementAccount("국민은행", "******-**-**1234", "박정훈"));
        assertThat(result.pendingSettlementAccountChange()).isNull();
        assertThat(result.pendingBusinessInformationChange()).isNull();
    }

    @Test
    @DisplayName("확인 중인 정산 계좌 변경이 있으면 지금 계좌는 그대로 두고, 요청한 계좌를 가려서 함께 돌려준다")
    void showsPendingSettlementAccountChangeAlongsideCurrent() {
        long requestId = scene.requestOrganizerChangeService().request(new RequestOrganizerChangeCommand(
                acting(JUNGHOON, scene.twelveMonths), new RequestedOrganizerChange.NewSettlementAccount(WOORI)))
                .changeRequestId();

        OrganizerBusinessDetailsResult result = get(JUNGHOON, scene.twelveMonths);

        assertThat(result.settlementAccount()).isEqualTo(new MaskedSettlementAccount("국민은행", "******-**-**1234", "박정훈"));
        assertThat(result.pendingSettlementAccountChange()).satisfies(pending -> {
            assertThat(pending.changeRequestId()).isEqualTo(requestId);
            assertThat(pending.requestedAt()).isEqualTo(NOW);
            assertThat(pending.requested()).isEqualTo(new MaskedSettlementAccount("우리은행", "****-***-**8901", "박정훈"));
        });
        assertThat(result.pendingBusinessInformationChange()).isNull();
        assertThat(result.toString()).doesNotContain("1234", "8901", "우리은행");
    }

    @Test
    @DisplayName("사업자 주최자는 사업자 정보를 그대로 본다(판매자 정보로 공개되는 값) — 확인 중인 사업자 정보 변경도 함께")
    void businessOwnerSeesBusinessInformationAndPendingChange() {
        com.ticketon.domain.organizer.BusinessInformation moved = new com.ticketon.domain.organizer.BusinessInformation(
                "늦여름 레코드", "최여름", "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"),
                BusinessRegistrationNumber.of("123-45-67890"));
        scene.requestOrganizerChangeService().request(new RequestOrganizerChangeCommand(acting(YEOREUM, scene.lateSummer),
                new RequestedOrganizerChange.NewBusinessInformation(moved)));

        OrganizerBusinessDetailsResult result = get(YEOREUM, scene.lateSummer);

        assertThat(result.businessStatus()).isEqualTo(BusinessStatus.BUSINESS);
        assertThat(result.businessInformation()).isEqualTo(new BusinessInformation("늦여름 레코드", "최여름",
                "서울특별시 마포구 와우산로 12", "02-123-4567", "1234567890"));
        assertThat(result.settlementAccount()).isEqualTo(new MaskedSettlementAccount("신한은행", "***-***-**6789", "늦여름 레코드"));
        assertThat(result.pendingBusinessInformationChange().requested()).isEqualTo(new BusinessInformation("늦여름 레코드",
                "최여름", "서울특별시 마포구 독막로 7", "02-765-4321", "1234567890"));
        assertThat(result.pendingSettlementAccountChange()).isNull();
    }

    @Test
    @DisplayName("운영자는 보지 못한다 (OWNER_ONLY_ACTION), 바깥 사람도 (NOT_ORGANIZER_STAFF)")
    void onlyOwnerSees() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> get(PLANNER, scene.twelveMonths))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.OWNER_ONLY_ACTION));
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> get(DOHYUN, scene.twelveMonths))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
    }

    @Test
    @DisplayName("닫힌 주최자는 보지 못한다 — 운영진이 남지 않았다")
    void closedOrganizerIsNotShown() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> get(JUNGHOON, scene.closedSpring));
    }
}
