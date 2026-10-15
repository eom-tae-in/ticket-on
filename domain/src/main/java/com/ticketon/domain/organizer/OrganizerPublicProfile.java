package com.ticketon.domain.organizer;

import java.util.List;

/**
 * 주최자 공개 정보 — 소개 · SNS 링크 · 로고. 주최자 페이지에 로그인 없이 누구나 본다(docs/01 "공개 페이지 — 주최자 페이지").
 * <p>
 * <b>셋 다 선택이고 운영진 누구나 편집한다</b>({@link StaffAction#EDIT_PUBLIC_PROFILE}). 여기의 SNS 링크는 심사용으로 신청서에 낸 링크와
 * 별개다(docs/01 "공개 페이지 — 편집"). 로고가 없으면 이름 첫 글자 + 정해진 색으로 보인다.
 *
 * @param introduction 소개. 앞뒤 공백을 걷고, 비었거나 공백뿐이면 없는 것(null)으로 둔다.
 * @param snsLinks     SNS 링크. 없으면 빈 목록. null도 빈 목록으로 받는다.
 * @param logo         로고. 없으면 null.
 */
public record OrganizerPublicProfile(String introduction, List<ExternalLink> snsLinks, OrganizerLogo logo) {

    private static final OrganizerPublicProfile EMPTY = new OrganizerPublicProfile(null, List.of(), null);

    public OrganizerPublicProfile {
        if (introduction != null) {
            introduction = introduction.strip();
            if (introduction.isEmpty()) {
                introduction = null;
            }
        }
        snsLinks = snsLinks == null ? List.of() : List.copyOf(snsLinks);
    }

    /** 아무것도 적지 않은 공개 정보 — 승인 직후의 주최자. */
    public static OrganizerPublicProfile empty() {
        return EMPTY;
    }

    /** 로고가 있는지. 없으면 이름 첫 글자 + 정해진 색으로 보인다. */
    public boolean hasLogo() {
        return logo != null;
    }
}
