package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.APPROVED_AT;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.GetStaffListQuery;
import com.ticketon.application.organizer.port.in.StaffListResult;
import com.ticketon.application.organizer.port.out.StaffListQueryPort;
import com.ticketon.application.organizer.query.CurrentStaffMember;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 극단 열두달의 운영진 목록({@link OrganizerStaffScene}). 조회 포트는 넣어 둔 줄을 그대로 돌려준다. */
@DisplayName("운영진 목록 조회 유스케이스")
class GetStaffListServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();

    private final List<CurrentStaffMember> twelveMonthsStaff = List.of(
            new CurrentStaffMember(JUNGHOON, "조용한고래", StaffRole.OWNER, APPROVED_AT),
            new CurrentStaffMember(PLANNER, "푸른바람", StaffRole.OPERATOR, APPROVED_AT.plusSeconds(86_400)));

    private final StaffListQueryPort queryPort =
            organizerId -> organizerId == scene.twelveMonths ? twelveMonthsStaff : List.of();

    private final GetStaffListService service = new GetStaffListService(scene.checkStaffPermissionService, queryPort);

    @Test
    @DisplayName("운영자도 운영진 목록을 본다 — 닉네임 · 역할 · 소속 일시가 조회한 순서 그대로 나온다")
    void operatorSeesStaffList() {
        StaffListResult result = service.getStaffList(new GetStaffListQuery(acting(PLANNER, scene.twelveMonths)));

        assertThat(result.members()).containsExactly(
                new StaffListResult.Member(JUNGHOON, "조용한고래", StaffRole.OWNER, APPROVED_AT),
                new StaffListResult.Member(PLANNER, "푸른바람", StaffRole.OPERATOR, APPROVED_AT.plusSeconds(86_400)));
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 403 NOT_ORGANIZER_STAFF다")
    void outsiderCannotSee() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> service.getStaffList(new GetStaffListQuery(acting(DOHYUN, scene.twelveMonths))))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
    }
}
