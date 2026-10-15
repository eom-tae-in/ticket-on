package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.GetStaffPublicProfileQuery;
import com.ticketon.application.organizer.port.in.StaffPublicProfileResult;
import com.ticketon.application.organizer.port.in.StaffPublicProfileResult.ParticipatedOrganizer;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 박정훈(docs/15)의 운영진 프로필을 메모리 저장소로 돌린다 — docs/01 "운영진 프로필" 예시 그대로 극단 열두달 소유자 · 2019~(지금), 늦여름 운영자
 * · 2023~2024(활동 종료).
 */
@DisplayName("운영진 프로필 조회 유스케이스 — 로그인 없이 누구나")
class GetStaffPublicProfileServiceTest {

    private static final long JUNGHOON = 1L;
    private static final long YEOREUM = 2L;
    private static final long NEWCOMER = 3L;

    private static final Instant MARCH_2019 = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));
    private static final Instant MAY_2023 = KoreanTime.toInstant(LocalDateTime.of(2023, 5, 1, 10, 0));
    private static final Instant NOVEMBER_2024 = KoreanTime.toInstant(LocalDateTime.of(2024, 11, 30, 18, 0));

    private final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository();
    private final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();

    private final GetStaffPublicProfileService service =
            new GetStaffPublicProfileService(staffAccountRepository, staffRepository, organizerRepository);

    private final long twelveMonths;
    private final long lateSummer;

    GetStaffPublicProfileServiceTest() {
        staffAccountRepository.storeActive(JUNGHOON, "박정훈", "조용한고래", "junghoon@example.com");
        staffAccountRepository.introduce(JUNGHOON, "극단에서 연출하고 있습니다");
        staffAccountRepository.storeActive(YEOREUM, "최여름", "늦은여름", "yeoreum@example.com");
        staffAccountRepository.storeActive(NEWCOMER, "정새내", "새내기고래", "newcomer@example.com");

        twelveMonths = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE).id().orElseThrow();
        staffRepository.store(OrganizerStaff.restore(twelveMonths, List.of(
                new StaffMembership(twelveMonths, JUNGHOON, StaffRole.OWNER, MARCH_2019, null))));

        lateSummer = organizerRepository.store("늦여름", OrganizerStatus.ACTIVE).id().orElseThrow();
        staffRepository.store(OrganizerStaff.restore(lateSummer, List.of(
                new StaffMembership(lateSummer, YEOREUM, StaffRole.OWNER, MARCH_2019, null),
                new StaffMembership(lateSummer, JUNGHOON, StaffRole.OPERATOR, MAY_2023, NOVEMBER_2024))));
    }

    @Test
    @DisplayName("소속 두 곳(지금 · 지난)이 기간과 함께 나온다 — 극단 열두달 · 소유자 · 2019~, 늦여름 · 운영자 · 2023~2024 (활동 종료)")
    void currentAndPastAffiliationsWithPeriods() {
        StaffPublicProfileResult result = service.getProfile(new GetStaffPublicProfileQuery(JUNGHOON));

        assertThat(result).isEqualTo(new StaffPublicProfileResult("조용한고래", "극단에서 연출하고 있습니다", "조", 1, List.of(
                new ParticipatedOrganizer(twelveMonths, "극단 열두달", StaffRole.OWNER, MARCH_2019, null, false),
                new ParticipatedOrganizer(lateSummer, "늦여름", StaffRole.OPERATOR, MAY_2023, NOVEMBER_2024, true))));
    }

    @Test
    @DisplayName("결과에 실명 · 이메일 칸이 없고, 그 값도 담기지 않는다 (docs/01 \"운영진 프로필 — 보이지 않는 것\")")
    void noPersonalInformation() {
        StaffPublicProfileResult result = service.getProfile(new GetStaffPublicProfileQuery(JUNGHOON));

        assertThat(Arrays.stream(StaffPublicProfileResult.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("nickname", "introduction", "initial", "paletteIndex", "participatedOrganizers");
        assertThat(Arrays.stream(ParticipatedOrganizer.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("organizerId", "organizerName", "role", "startedAt", "endedAt", "activityEnded");
        assertThat(result.toString()).doesNotContain("박정훈").doesNotContain("junghoon@example.com");
    }

    @Test
    @DisplayName("닫힌 주최자는 활동 종료로 남고, 영구 정지된 주최자는 목록에서 빠진다")
    void closedStaysAndSuspendedIsExcluded() {
        long springDay = organizerRepository.store("극단 봄날", OrganizerStatus.CLOSED).id().orElseThrow();
        staffRepository.store(OrganizerStaff.restore(springDay, List.of(
                new StaffMembership(springDay, JUNGHOON, StaffRole.OWNER, MAY_2023, NOVEMBER_2024))));
        long suspended = organizerRepository.store("정지된 극단", OrganizerStatus.PERMANENTLY_SUSPENDED).id().orElseThrow();
        staffRepository.store(OrganizerStaff.restore(suspended, List.of(
                new StaffMembership(suspended, JUNGHOON, StaffRole.OPERATOR, MAY_2023, NOVEMBER_2024))));

        StaffPublicProfileResult result = service.getProfile(new GetStaffPublicProfileQuery(JUNGHOON));

        assertThat(result.participatedOrganizers()).extracting(ParticipatedOrganizer::organizerName)
                .containsExactly("극단 열두달", "늦여름", "극단 봄날");
        assertThat(result.participatedOrganizers().getLast().activityEnded()).isTrue();
    }

    @Test
    @DisplayName("주최자에 소속된 적이 없는 운영진 로그인은 아직 운영진이 아니라 404다")
    void neverAffiliatedIsNotFound() {
        assertNotFound(NEWCOMER);
    }

    @Test
    @DisplayName("탈퇴한 운영진 로그인은 404다 — 소속 이력이 남아 있어도 (확인 필요 #28)")
    void withdrawnIsNotFound() {
        staffAccountRepository.markWithdrawn(JUNGHOON);

        assertNotFound(JUNGHOON);
    }

    @Test
    @DisplayName("없는 운영진 계정이면 404다")
    void unknownIsNotFound() {
        assertNotFound(999L);
    }

    private void assertNotFound(long staffAccountId) {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getProfile(new GetStaffPublicProfileQuery(staffAccountId)))
                .extracting(DomainException::errorCode)
                .isEqualTo(OrganizerErrorCode.STAFF_PROFILE_NOT_FOUND);
    }
}
