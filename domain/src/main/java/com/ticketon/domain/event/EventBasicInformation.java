package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.ExternalLink;
import java.util.Objects;

/**
 * 행사 기본 정보 — 위저드 1단계에서 적는 것(docs/03 "기본 정보", "행사 만들기 위저드").
 * <p>
 * <b>필수</b> — 제목 · 유형 · 카테고리 · 소개 · 장소(공연장 이름 · 주소). <b>선택</b> — 상세 안내 · 지도 링크 · 공개용 연락처.
 * 필수 항목은 비울 수 없다. 1단계에서 함께 받는 포스터는 행사를 만든 뒤 따로 올리므로 여기 두지 않는다({@link EventPoster}).
 * <b>회차 방식(회차형 / 종일형)은 여기서 고르지 않는다</b> — 유형과 다른 축이라 회차를 만드는 2단계에서 고른다(docs/03 "기본 정보").
 * <p>
 * 카테고리는 유형에 맞아야 한다 — 유형을 고르면 그에 맞는 목록에서 고른다(docs/03 "카테고리"). 주최자가 고른 값은
 * {@link EventCategoryCatalog#select}로 카테고리 선택을 만들어 넘긴다.
 * <p>
 * 지도는 API로 붙이지 않는다 — 비용과 복잡도에 비해 얻는 게 적어, 외부 지도 주소를 링크로 둔다(docs/03, docs/99 B "지도 API 임베드").
 * 누구나 로그인 없이 보는 행사 페이지에서 누르는 링크라 http(s) 주소만 받는다({@link ExternalLink}).
 * <p>
 * 소개 · 상세 안내는 앞뒤 공백을 걷는다. 상세 안내가 비었거나 공백뿐이면 없는 것(null)으로 둔다. 소개 · 장소 · 상세 안내의 길이는 문서에
 * 상한이 없어 두지 않는다.
 *
 * @param title         제목
 * @param type          유형
 * @param category      카테고리 — 정식 카테고리, 또는 "기타" + 장르명
 * @param introduction  소개. 앞뒤 공백을 걷는다.
 * @param venue         장소 — 공연장 이름 · 주소
 * @param venueDetails  상세 안내 — "지하 1층", "주차 불가" 등. 없으면 null
 * @param mapLink       지도 링크 — 외부 지도 주소. 없으면 null
 * @param publicContact 공개용 연락처 — 적으면 행사 페이지에 보인다. 없으면 null
 */
public record EventBasicInformation(EventTitle title, EventType type, EventCategorySelection category, String introduction,
        Venue venue, String venueDetails, ExternalLink mapLink, EventPublicContact publicContact) {

    public EventBasicInformation {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(introduction, "introduction");
        Objects.requireNonNull(venue, "venue");
        introduction = introduction.strip();
        if (introduction.isEmpty()) {
            throw new IllegalArgumentException("소개가 비어 있습니다");
        }
        if (category.type() != type) {
            throw new RuleViolationException(EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE,
                    "유형: " + type + ", 카테고리의 유형: " + category.type());
        }
        if (venueDetails != null) {
            venueDetails = venueDetails.strip();
            if (venueDetails.isEmpty()) {
                venueDetails = null;
            }
        }
    }
}
