package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewQuery;
import com.ticketon.application.admin.port.in.OrganizerApplicationReviewResult;
import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.HolidayCalendar;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerReviewChecklistItem;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OrganizerType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 심사 화면 유스케이스 (docs/01 \"심사 화면\", \"닫힌 주최자를 다시 열 때\")")
class GetOrganizerApplicationReviewServiceTest {

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final long DOHYUN_STAFF = 2L;

    private final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    private final FakeOrganizerApplicationRepository applicationRepository = new FakeOrganizerApplicationRepository();
    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();
    private final FakeOrganizerApplicationReviewQueryPort reviewQueryPort = new FakeOrganizerApplicationReviewQueryPort();

    private final GetOrganizerApplicationReviewService service = new GetOrganizerApplicationReviewService(
            adminAccountRepository, applicationRepository, reviewQueryPort,
            new BusinessDayCalculator(HolidayCalendar.none()));

    private long adminId;

    @BeforeEach
    void storeAdmin() {
        adminId = adminAccountRepository.store("dohyun@ticket-on.example", FakePasswordHashPort.hashOf("admin-1234"),
                AdminGrade.ADMIN, SUBMITTED_AT);
    }

    @Test
    @DisplayName("사업자 신청서 전체가 나온다 — 담당자 연락처 · 링크 순서 · 사업자 정보 · 정산 계좌까지")
    void showsWholeBusinessApplication() {
        long organizerId = organizerRepository.store("늦여름", OrganizerStatus.UNDER_REVIEW);
        long applicationId = applicationRepository.store(organizerId, DOHYUN_STAFF, OrganizerApplicationForms.lateSummer(),
                SUBMITTED_AT);

        OrganizerApplicationReviewResult review = service.getReview(query(applicationId));

        assertThat(review.applicationId()).isEqualTo(applicationId);
        assertThat(review.organizerId()).isEqualTo(organizerId);
        assertThat(review.applicantStaffAccountId()).isEqualTo(DOHYUN_STAFF);
        assertThat(review.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
        assertThat(review.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(review.reviewedAt()).isNull();
        assertThat(review.rejectionReason()).isNull();
        assertThat(review.form()).isEqualTo(new OrganizerApplicationReviewResult.Form(OrganizerType.GROUP, "늦여름",
                "010-9876-5432", "hello@late-summer.example",
                List.of("https://late-summer.example", "https://blog.example/late-summer"),
                "인디 페스티벌 3회 개최. 2027년 5월 3일권 페스티벌 계획.", BusinessStatus.BUSINESS,
                new OrganizerApplicationReviewResult.BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                        "02-123-4567", "1234567890"),
                new OrganizerApplicationReviewResult.SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드")));
    }

    @Test
    @DisplayName("비사업자 신청서는 사업자 정보가 비어 있다")
    void nonBusinessHasNoBusinessInformation() {
        long applicationId = storedTwelveMonths();

        OrganizerApplicationReviewResult review = service.getReview(query(applicationId));

        assertThat(review.form().businessStatus()).isEqualTo(BusinessStatus.NON_BUSINESS);
        assertThat(review.form().businessInformation()).isNull();
    }

    @Test
    @DisplayName("체크리스트 세 항목과 반려 사유 템플릿 셋이 문서 순서대로 나온다")
    void showsChecklistAndRejectionTemplates() {
        OrganizerApplicationReviewResult review = service.getReview(query(storedTwelveMonths()));

        assertThat(review.checklist()).containsExactly(OrganizerReviewChecklistItem.CONTACT_REACHED_BY_PHONE,
                OrganizerReviewChecklistItem.EXISTENCE_CONFIRMED_BY_LINKS_AND_HISTORY,
                OrganizerReviewChecklistItem.EVENT_PLAN_IS_CONCRETE);
        assertThat(review.rejectionReasonTemplates())
                .containsExactly("연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음");
    }

    @Test
    @DisplayName("심사 목표일은 제출한 날부터 영업일 3일 — 2026-10-15(목) 제출이면 2026-10-20(화)")
    void showsReviewDueDate() {
        assertThat(service.getReview(query(storedTwelveMonths())).reviewDueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("같은 소유자 또는 같은 단체명의 닫힌 주최자를 신청한 사람과 신청한 이름으로 찾아 함께 보인다")
    void showsSimilarClosedOrganizers() {
        long applicationId = storedTwelveMonths();
        SimilarClosedOrganizer sameName = new SimilarClosedOrganizer(8, "극단 열두달", false, true);
        SimilarClosedOrganizer sameOwner = new SimilarClosedOrganizer(9, "극단 봄날", true, false);
        reviewQueryPort.answer(List.of(sameName, sameOwner));

        OrganizerApplicationReviewResult review = service.getReview(query(applicationId));

        assertThat(review.similarClosedOrganizers()).containsExactly(sameName, sameOwner);
        assertThat(reviewQueryPort.searchedApplicant()).isEqualTo(1L);
        assertThat(reviewQueryPort.searchedName()).isEqualTo(OrganizerName.of("극단 열두달"));
    }

    @Test
    @DisplayName("이미 반려한 신청서도 결과(상태 · 처리 일시 · 사유)와 함께 본다")
    void showsReviewedApplication() {
        long applicationId = storedTwelveMonths();
        Instant rejectedAt = SUBMITTED_AT.plusSeconds(86_400);
        applicationRepository.rejectInTable(applicationId, "연락이 닿지 않음", rejectedAt);

        OrganizerApplicationReviewResult review = service.getReview(query(applicationId));

        assertThat(review.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
        assertThat(review.reviewedAt()).isEqualTo(rejectedAt);
        assertThat(review.rejectionReason()).isEqualTo("연락이 닿지 않음");
    }

    @Test
    @DisplayName("없는 신청서면 찾을 수 없음 APPLICATION_NOT_FOUND")
    void unknownApplication() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> service.getReview(query(999)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.APPLICATION_NOT_FOUND));
    }

    @Test
    @DisplayName("문자열로 바꿔도 담당자 연락처 · 이메일 · 사업자 정보 · 정산 계좌는 드러나지 않는다")
    void toStringHidesSensitiveValues() {
        long organizerId = organizerRepository.store("늦여름", OrganizerStatus.UNDER_REVIEW);
        long applicationId = applicationRepository.store(organizerId, DOHYUN_STAFF, OrganizerApplicationForms.lateSummer(),
                SUBMITTED_AT);

        String text = service.getReview(query(applicationId)).toString();

        assertThat(text).contains("늦여름").doesNotContain("010-9876-5432", "hello@late-summer.example",
                "1234567890", "와우산로", "110-123-456789");
    }

    private long storedTwelveMonths() {
        long organizerId = organizerRepository.store("극단 열두달", OrganizerStatus.UNDER_REVIEW);
        return applicationRepository.store(organizerId, 1L, OrganizerApplicationForms.twelveMonths(), SUBMITTED_AT);
    }

    private GetOrganizerApplicationReviewQuery query(long applicationId) {
        return new GetOrganizerApplicationReviewQuery(AccountRef.of(AccountKind.ADMIN, adminId), applicationId);
    }
}
