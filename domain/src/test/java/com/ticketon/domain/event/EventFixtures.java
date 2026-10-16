package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.ExternalLink;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * 행사 테스트에서 함께 쓰는 값과 확인. docs/15의 두 주최자가 여는 행사다 — 박정훈이 대표인 극단 "열두달"의 정기공연(공연 · 연극)과
 * 이도현이 운영자로 있는 "늦여름"의 페스티벌(페스티벌 · 음악). 문서 예시의 "○○아트홀", "지하 1층", "주차 불가"를 옮겼다(docs/03 "기본 정보").
 */
final class EventFixtures {

    /** 극단 열두달의 주최자 id. */
    static final long TWELVE_MONTHS_ORGANIZER = 12L;

    private EventFixtures() {
    }

    /** 극단 열두달의 정기공연 기본 정보 — 공연 · 연극, 선택 항목까지 모두 적었다. */
    static EventBasicInformation twelveMonthsSpringPlay() {
        return new EventBasicInformation(
                EventTitle.of("2026 봄 정기공연 〈열두 번째 달〉"),
                EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.\n러닝타임 100분, 인터미션 없음.",
                new Venue("○○아트홀", "서울특별시 종로구 대학로 12"),
                "지하 1층, 주차 불가",
                ExternalLink.of("https://map.example/place/12"),
                EventPublicContact.of("twelve-months@example.com"));
    }

    /** 늦여름 페스티벌 기본 정보 — 페스티벌 · 음악, 선택 항목은 비웠다. */
    static EventBasicInformation lateSummerFestival() {
        return new EventBasicInformation(
                EventTitle.of("늦여름 페스티벌 2027"),
                EventType.FESTIVAL,
                EventCategoryCatalog.initial().select(EventType.FESTIVAL, "음악", null),
                "인디 밴드 열두 팀이 사흘 동안 무대에 오릅니다.",
                new Venue("늦여름 야외무대", "경기도 가평군 가평읍 강변로 3"),
                null,
                null,
                null);
    }

    /** 규칙 위반으로 막히고, 그 오류 코드가 기대한 것인지 본다. */
    static void assertRejected(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
