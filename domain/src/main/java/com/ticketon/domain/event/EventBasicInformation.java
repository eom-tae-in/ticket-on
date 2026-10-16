package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.ExternalLink;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

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
 * <p>
 * 언제 무엇을 고칠 수 있는지는 항목마다 다르다 — 예매가 열린 뒤에도 제목 · 소개는 자유지만 장소는 관리자 승인을 거친다(docs/04 "예매가 열린 뒤
 * 수정 범위"). 그래서 바꿀 때는 바뀐 항목을 상태별 수정 범위 표의 단위로 나눠 본다({@link #changedEditTargets}).
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

    /**
     * 이 기본 정보를 그 기본 정보로 바꾸면 바뀌는 수정 항목 — 상태별 수정 범위 표({@link EventEditScope})가 판정하는 단위로 나눈다.
     * <ul>
     *   <li>제목 · 소개 → {@link EventEditTarget#TITLE_POSTER_DESCRIPTION}</li>
     *   <li>유형 · 카테고리 → {@link EventEditTarget#TYPE_AND_CATEGORY}</li>
     *   <li>장소(공연장 이름 · 주소) → {@link EventEditTarget#VENUE}</li>
     *   <li>상세 안내 · 지도 링크 · 공개용 연락처 → {@link EventEditTarget#VENUE_GUIDE_AND_CONTACT}</li>
     * </ul>
     * 위저드는 단계의 칸을 모두 다시 보내므로, 바뀌지 않은 항목은 담지 않는다 — 예매가 열린 뒤 제목만 고치면서 장소를 그대로 보내도 장소 변경으로
     * 보지 않는다. 같으면 빈 집합이다.
     */
    public Set<EventEditTarget> changedEditTargets(EventBasicInformation changed) {
        Objects.requireNonNull(changed, "changed");
        Set<EventEditTarget> targets = EnumSet.noneOf(EventEditTarget.class);
        if (!title.equals(changed.title) || !introduction.equals(changed.introduction)) {
            targets.add(EventEditTarget.TITLE_POSTER_DESCRIPTION);
        }
        if (type != changed.type || !category.equals(changed.category)) {
            targets.add(EventEditTarget.TYPE_AND_CATEGORY);
        }
        if (!venue.equals(changed.venue)) {
            targets.add(EventEditTarget.VENUE);
        }
        if (!Objects.equals(venueDetails, changed.venueDetails) || !Objects.equals(mapLink, changed.mapLink)
                || !Objects.equals(publicContact, changed.publicContact)) {
            targets.add(EventEditTarget.VENUE_GUIDE_AND_CONTACT);
        }
        return targets;
    }
}
