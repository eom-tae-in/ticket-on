package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import java.util.Objects;

/**
 * 위저드 1단계에 적은 기본 정보 — 행사 만들기와 1단계 저장이 함께 쓴다(docs/03 "기본 정보"). 포스터는 따로 올린다(#40).
 * <p>
 * 카테고리는 고른 이름 그대로다 — 그 유형의 정식 카테고리 목록에 있는지, '기타'면 장르명이 있는지는 저장된 목록으로 본다
 * ({@link #toBasicInformation}, {@link EventCategoryCatalog#select}). 공개용 연락처는 값을 가리는 값 객체라 문자열로 바꿔도 새지 않는다.
 *
 * @param title         제목
 * @param type          유형
 * @param categoryName  고른 카테고리 이름 — 기타면 "기타"
 * @param genreName     기타를 고르고 적은 장르명. 정식 카테고리면 null이나 빈 값
 * @param introduction  소개
 * @param venue         장소 — 공연장 이름 · 주소
 * @param venueDetails  상세 안내. 없으면 null이나 빈 값
 * @param mapLink       지도 링크. 없으면 null
 * @param publicContact 공개용 연락처. 없으면 null
 */
public record EventBasicInformationItems(EventTitle title, EventType type, String categoryName, String genreName,
        String introduction, Venue venue, String venueDetails, ExternalLink mapLink, EventPublicContact publicContact) {

    public EventBasicInformationItems {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(categoryName, "categoryName");
        Objects.requireNonNull(introduction, "introduction");
        Objects.requireNonNull(venue, "venue");
    }

    /**
     * 저장된 정식 카테고리 목록으로 카테고리를 골라 기본 정보를 만든다.
     *
     * @throws com.ticketon.domain.error.RuleViolationException 그 유형의 목록에 없는 카테고리, 기타인데 장르명이 없거나 정식 카테고리인데 장르명을
     *                                                          적었으면({@link EventCategoryCatalog#select})
     */
    public EventBasicInformation toBasicInformation(EventCategoryCatalog catalog) {
        return new EventBasicInformation(title, type, catalog.select(type, categoryName, genreName), introduction, venue,
                venueDetails, mapLink, publicContact);
    }
}
