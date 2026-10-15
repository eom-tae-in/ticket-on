package com.ticketon.application.admin.service;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.util.List;

/**
 * 심사 테스트의 신청서 — docs/15의 두 주최자. 박정훈이 대표인 극단 "열두달"(비사업자 단체)과 레이블 겸 공연기획 팀 "늦여름"(사업자).
 */
final class OrganizerApplicationForms {

    private OrganizerApplicationForms() {
    }

    static OrganizerApplicationForm twelveMonths() {
        return new OrganizerApplicationForm(
                OrganizerType.GROUP,
                OrganizerName.of("극단 열두달"),
                TelephoneNumber.of("010-2345-6789"),
                EmailAddress.of("twelve-months@example.com"),
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater")),
                "2019년부터 소극장 정기공연을 해마다 두 번 올립니다. 2025 가을 정기공연, 2025 낭독회.",
                BusinessDeclaration.nonBusiness(),
                new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));
    }

    static OrganizerApplicationForm lateSummer() {
        return new OrganizerApplicationForm(
                OrganizerType.GROUP,
                OrganizerName.of("늦여름"),
                TelephoneNumber.of("010-9876-5432"),
                EmailAddress.of("hello@late-summer.example"),
                List.of(ExternalLink.of("https://late-summer.example"), ExternalLink.of("https://blog.example/late-summer")),
                "인디 페스티벌 3회 개최. 2027년 5월 3일권 페스티벌 계획.",
                BusinessDeclaration.business(new BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                        TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"))),
                new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }
}
