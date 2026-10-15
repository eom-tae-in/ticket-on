package com.ticketon.domain.organizer;

import java.util.List;
import java.util.Objects;

/**
 * 주최자 공개 정보 — 소개 · SNS 링크 · 로고. 주최자 페이지에 로그인 없이 누구나 본다(docs/01 "공개 페이지 — 주최자 페이지").
 * <p>
 * <b>셋 다 선택이고 운영진 누구나 편집한다</b>({@link StaffAction#EDIT_PUBLIC_PROFILE}). 여기의 SNS 링크는 심사용으로 신청서에 낸 링크와
 * 별개다(docs/01 "공개 페이지 — 편집"). 로고가 없으면 이름 첫 글자 + 정해진 색으로 보인다.
 * <p>
 * 소개 길이와 SNS 링크 개수는 문서에 상한이 없어 두지 않는다(신청서의 글 칸 · 링크와 같다). SNS 링크는 적은 순서대로 보인다.
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

    /** 소개 · SNS 링크만 바꾼 공개 정보. 로고는 그대로다. */
    public OrganizerPublicProfile withIntroductionAndSnsLinks(String newIntroduction, List<ExternalLink> newSnsLinks) {
        return new OrganizerPublicProfile(newIntroduction, newSnsLinks, logo);
    }

    /** 로고만 바꾼 공개 정보. */
    public OrganizerPublicProfile withLogo(OrganizerLogo newLogo) {
        return new OrganizerPublicProfile(introduction, snsLinks, Objects.requireNonNull(newLogo, "newLogo"));
    }

    /** 로고를 뺀 공개 정보 — 로고 자리에 이름 첫 글자 + 정해진 색이 보인다. */
    public OrganizerPublicProfile withoutLogo() {
        return new OrganizerPublicProfile(introduction, snsLinks, null);
    }
}
