package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.BusinessItems;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationCommand;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationResult;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 주최자 신청서 제출 유스케이스. 신청서는 docs/15의 두 주최자 — 박정훈이 대표인 극단 "열두달"(비사업자 단체)과 사업자인 "늦여름" — 다.
 */
@DisplayName("주최자 신청서 제출 유스케이스")
class SubmitOrganizerApplicationServiceTest {

    private static final AccountRef JUNGHOON = AccountRef.of(AccountKind.STAFF, 1);

    private static final AccountRef DOHYUN = AccountRef.of(AccountKind.STAFF, 4);

    private final MutableClock clock = MutableClock.at(LocalDateTime.of(2026, 10, 15, 10, 0));

    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();

    private final FakeOrganizerApplicationRepository applicationRepository = new FakeOrganizerApplicationRepository();

    private final SubmitOrganizerApplicationService service =
            new SubmitOrganizerApplicationService(organizerRepository, applicationRepository, clock);

    @Test
    @DisplayName("신청하면 주최자가 '심사 중'으로 생기고, 신청서는 심사 중으로 그 주최자를 가리킨다")
    void submissionCreatesOrganizerUnderReview() {
        SubmitOrganizerApplicationResult result = service.submit(twelveMonths(JUNGHOON));

        Organizer organizer = organizerRepository.savedOrganizers().getFirst();
        assertThat(organizer.status()).isEqualTo(OrganizerStatus.UNDER_REVIEW);
        assertThat(organizer.name()).isEqualTo(OrganizerName.of("극단 열두달"));
        OrganizerApplication application = applicationRepository.findById(result.applicationId()).orElseThrow();
        assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
        assertThat(application.organizerId()).isEqualTo(organizer.id().getAsLong());
        assertThat(application.applicantStaffAccountId()).isEqualTo(JUNGHOON.id());
        assertThat(application.submittedAt()).isEqualTo(clock.instant());
        assertThat(result).isEqualTo(new SubmitOrganizerApplicationResult(application.id().getAsLong(), "극단 열두달",
                clock.instant()));
    }

    @Test
    @DisplayName("신청 항목을 그대로 저장한다 — 링크 순서, 정산 계좌, 비사업자는 사업자 정보 없음")
    void storesFormAsWritten() {
        long applicationId = service.submit(twelveMonths(JUNGHOON)).applicationId();

        var form = applicationRepository.findById(applicationId).orElseThrow().form();
        assertThat(form.organizerType()).isEqualTo(OrganizerType.GROUP);
        assertThat(form.contactPhoneNumber()).isEqualTo(TelephoneNumber.of("010-2345-6789"));
        assertThat(form.email()).isEqualTo(EmailAddress.of("twelve-months@example.com"));
        assertThat(form.websiteAndSnsLinks()).containsExactly(
                ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
                ExternalLink.of("https://blog.example/twelve-months"));
        assertThat(form.businessStatus()).isEqualTo(BusinessStatus.NON_BUSINESS);
        assertThat(form.businessDeclaration().businessInformation()).isNull();
        assertThat(form.settlementAccount()).isEqualTo(new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));
    }

    @Test
    @DisplayName("사업자는 사업자 정보 다섯 가지를 함께 저장한다 — 사업자등록번호는 숫자만 남긴다")
    void businessApplicationStoresBusinessInformation() {
        long applicationId = service.submit(lateSummer(DOHYUN, lateSummerItems())).applicationId();

        assertThat(applicationRepository.findById(applicationId).orElseThrow().form().businessDeclaration()
                .businessInformation()).isEqualTo(new BusinessInformation("늦여름 레코드", "최여름",
                        "서울특별시 마포구 와우산로 12", TelephoneNumber.of("02-123-4567"),
                        new BusinessRegistrationNumber("1234567890")));
    }

    @Nested
    @DisplayName("사업자 항목 (docs/01 \"신청 항목\" — 사업자만 ✓)")
    class BusinessItemsRule {

        @Test
        @DisplayName("사업자인데 사업장 주소와 사업자등록번호가 빠지면 거부하고, 주최자도 신청서도 만들지 않는다")
        void businessWithMissingItemsIsRejected() {
            BusinessItems missingTwo = new BusinessItems("늦여름 레코드", "최여름", " ", "02-123-4567", null);

            assertRejected(() -> service.submit(lateSummer(DOHYUN, missingTwo)),
                    OrganizerErrorCode.BUSINESS_INFORMATION_MISSING);
            assertThat(organizerRepository.savedOrganizers()).isEmpty();
            assertThat(applicationRepository.savedApplications()).isEmpty();
        }

        @Test
        @DisplayName("사업자인데 사업자 항목을 하나도 적지 않으면 거부한다")
        void businessWithoutItemsIsRejected() {
            assertRejected(() -> service.submit(lateSummer(DOHYUN, BusinessItems.none())),
                    OrganizerErrorCode.BUSINESS_INFORMATION_MISSING);
            assertThat(organizerRepository.savedOrganizers()).isEmpty();
        }

        @Test
        @DisplayName("비사업자가 사업자 항목을 적으면 거부한다 — 판매자 정보에 나갈 값이 사업자 여부와 어긋나지 않게")
        void nonBusinessWithItemsIsRejected() {
            SubmitOrganizerApplicationCommand nonBusinessWithTradeName = new SubmitOrganizerApplicationCommand(JUNGHOON,
                    OrganizerType.GROUP, OrganizerName.of("극단 열두달"), TelephoneNumber.of("010-2345-6789"),
                    EmailAddress.of("twelve-months@example.com"), List.of(), "정기공연 계획", BusinessStatus.NON_BUSINESS,
                    new BusinessItems("열두달 컴퍼니", null, null, null, null), twelveMonthsAccount());

            assertRejected(() -> service.submit(nonBusinessWithTradeName),
                    OrganizerErrorCode.BUSINESS_INFORMATION_FOR_NON_BUSINESS);
            assertThat(organizerRepository.savedOrganizers()).isEmpty();
        }
    }

    @Nested
    @DisplayName("다시 신청 (docs/01 \"반려 — 재신청은 제한 없이\", \"승인 전까지 '심사 중' 화면만\")")
    class Reapply {

        @Test
        @DisplayName("반려된 뒤에는 다시 신청할 수 있다 — 새 신청서와 새 심사 중 주최자가 생기고, 반려된 신청서는 사유와 함께 남는다")
        void reapplyAfterRejection() {
            long rejected = service.submit(twelveMonths(JUNGHOON)).applicationId();
            clock.advance(Duration.ofDays(2));
            applicationRepository.reject(rejected, "연락이 닿지 않음", clock.instant());
            clock.advance(Duration.ofHours(1));

            SubmitOrganizerApplicationResult reapplied = service.submit(twelveMonths(JUNGHOON));

            assertThat(reapplied.applicationId()).isNotEqualTo(rejected);
            assertThat(applicationRepository.findById(reapplied.applicationId()).orElseThrow().status())
                    .isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(applicationRepository.findById(rejected).orElseThrow().status())
                    .isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(organizerRepository.savedOrganizers()).hasSize(2)
                    .allSatisfy(organizer -> assertThat(organizer.status()).isEqualTo(OrganizerStatus.UNDER_REVIEW));
        }

        @Test
        @DisplayName("반려를 여러 번 받아도 계속 다시 신청할 수 있다 — 횟수 제한이 없다")
        void noLimitOnReapplication() {
            for (int attempt = 1; attempt <= 3; attempt++) {
                long applicationId = service.submit(twelveMonths(JUNGHOON)).applicationId();
                clock.advance(Duration.ofDays(1));
                applicationRepository.reject(applicationId, "정보가 부족함", clock.instant());
            }

            service.submit(twelveMonths(JUNGHOON));

            assertThat(applicationRepository.savedApplications()).hasSize(4);
        }

        @Test
        @DisplayName("심사 중인 신청이 있으면 새로 내지 않는다 — 주최자를 더 만들지 않는다")
        void notWhileUnderReview() {
            service.submit(twelveMonths(JUNGHOON));

            assertRejected(() -> service.submit(twelveMonths(JUNGHOON)), OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW);
            assertThat(organizerRepository.savedOrganizers()).hasSize(1);
            assertThat(applicationRepository.savedApplications()).hasSize(1);
        }

        @Test
        @DisplayName("다른 사람의 심사 중 신청은 상관없다")
        void otherApplicantsDoNotBlock() {
            service.submit(twelveMonths(JUNGHOON));

            service.submit(lateSummer(DOHYUN, lateSummerItems()));

            assertThat(applicationRepository.savedApplications()).hasSize(2);
        }

        @Test
        @DisplayName("확인은 통과했지만 저장 직전 같은 사람의 다른 신청이 먼저 저장되면 충돌이 그대로 나간다")
        void propagatesSubmissionRace() {
            applicationRepository.letAnotherSubmissionWinNextAdd();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> service.submit(twelveMonths(JUNGHOON)))
                    .extracting(DomainException::errorCode)
                    .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW);
        }
    }

    @Test
    @DisplayName("운영진 계정이 아니면 신청서 입력을 만들지 않는다")
    void onlyStaffAccount() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> twelveMonths(AccountRef.of(AccountKind.AUDIENCE, 7)));
    }

    @Test
    @DisplayName("입력을 글로 남길 때 연락처·사업자 정보·정산 계좌는 싣지 않는다")
    void commandTextHidesSensitiveValues() {
        String text = lateSummer(DOHYUN, lateSummerItems()).toString();

        assertThat(text).contains("STAFF:4", "늦여름")
                .doesNotContain("110-123-456789", "02-123-4567", "123-45-67890", "와우산로");
    }

    private static SubmitOrganizerApplicationCommand twelveMonths(AccountRef applicant) {
        return new SubmitOrganizerApplicationCommand(applicant, OrganizerType.GROUP, OrganizerName.of("극단 열두달"),
                TelephoneNumber.of("010-2345-6789"), EmailAddress.of("twelve-months@example.com"),
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
                        ExternalLink.of("https://blog.example/twelve-months")),
                "2019년부터 소극장 정기공연을 해마다 두 번 올립니다. 2025 가을 정기공연, 2025 낭독회.",
                BusinessStatus.NON_BUSINESS, BusinessItems.none(), twelveMonthsAccount());
    }

    private static SettlementAccount twelveMonthsAccount() {
        return new SettlementAccount("국민은행", "123456-78-901234", "박정훈");
    }

    private static SubmitOrganizerApplicationCommand lateSummer(AccountRef applicant, BusinessItems businessItems) {
        return new SubmitOrganizerApplicationCommand(applicant, OrganizerType.GROUP, OrganizerName.of("늦여름"),
                TelephoneNumber.of("010-9876-5432"), EmailAddress.of("hello@late-summer.example"),
                List.of(ExternalLink.of("https://late-summer.example")),
                "인디 페스티벌 3회 개최. 2027년 5월 3일권 페스티벌 계획.",
                BusinessStatus.BUSINESS, businessItems, new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }

    private static BusinessItems lateSummerItems() {
        return new BusinessItems("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12", "02-123-4567", "123-45-67890");
    }

    private static void assertRejected(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .extracting(DomainException::errorCode)
                .isEqualTo(expectedCode);
    }
}
