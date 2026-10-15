package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * 주최자 테스트에서 함께 쓰는 값과 확인. docs/15의 두 주최자다 — 박정훈이 대표인 극단 "열두달"(비사업자 단체)과
 * 이도현이 운영자로 있는 레이블 겸 공연기획 팀 "늦여름"(사업자).
 */
final class OrganizerFixtures {

    /** 신청서 제출 일시 — 2026-10-15 10:00 (한국 시각). */
    static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    /** 심사 일시 — 제출 이틀 뒤 2026-10-17 15:00. 영업일 3일 이내(docs/99 C "주최자 심사 목표"). */
    static final Instant REVIEWED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    /** 신청한 운영진 — 박정훈의 운영진 계정 id. */
    static final long JUNGHOON = 1L;

    /** 박정훈이 신청하면서 심사 중으로 만든 주최자 "극단 열두달"의 id. */
    static final long TWELVE_MONTHS_ORGANIZER = 12L;

    private OrganizerFixtures() {
    }

    /** 극단 열두달의 신청서 — 단체, 비사업자. */
    static OrganizerApplicationForm twelveMonthsForm() {
        return new OrganizerApplicationForm(
                OrganizerType.GROUP,
                OrganizerName.of("극단 열두달"),
                TelephoneNumber.of("010-2345-6789"),
                EmailAddress.of("twelve-months@example.com"),
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater")),
                "2019년부터 소극장 정기공연을 해마다 두 번 올립니다. 2025 가을 정기공연, 2025 낭독회.",
                BusinessDeclaration.nonBusiness(),
                twelveMonthsSettlementAccount());
    }

    static SettlementAccount twelveMonthsSettlementAccount() {
        return new SettlementAccount("국민은행", "123456-78-901234", "박정훈");
    }

    /** 늦여름의 사업자 정보. */
    static BusinessInformation lateSummerBusinessInformation() {
        return new BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"));
    }

    /** 늦여름의 신청서 — 단체, 사업자. */
    static OrganizerApplicationForm lateSummerForm() {
        return new OrganizerApplicationForm(
                OrganizerType.GROUP,
                OrganizerName.of("늦여름"),
                TelephoneNumber.of("02-123-4567"),
                EmailAddress.of("hello@late-summer.example"),
                List.of(ExternalLink.of("https://late-summer.example"), ExternalLink.of("https://blog.example/late-summer")),
                "인디 페스티벌 3회 개최. 2027년 5월 3일권 페스티벌 계획.",
                BusinessDeclaration.business(lateSummerBusinessInformation()),
                new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }

    /** 규칙 위반으로 막히고, 그 오류 코드가 기대한 것인지 본다. */
    static void assertRejected(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
