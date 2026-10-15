package com.ticketon.application.organizer.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 주최자 공개 정보 — 소개 · SNS 링크 · 로고와, 로고가 없을 때 로고 자리에 보이는 첫 글자 표시(docs/01 "공개 페이지 — 주최자 페이지").
 *
 * @param organizerId  주최자 id
 * @param introduction 소개 — 저장된 그대로. 적지 않았으면 null.
 * @param snsLinks     SNS 링크 — 적은 순서대로. 없으면 빈 목록
 * @param logoUrl      로고의 공개 주소(절대 주소). 로고가 없으면 null — 그때는 첫 글자 표시가 로고 자리에 보인다
 * @param initial      주최자명 첫 글자
 * @param paletteIndex 주최자마다 정해진 색의 팔레트 번호(0부터, 8가지). 이름을 바꿔도 그대로다
 */
public record OrganizerPublicProfileResult(long organizerId, String introduction, List<String> snsLinks, String logoUrl,
        String initial, int paletteIndex) {

    public OrganizerPublicProfileResult {
        snsLinks = List.copyOf(snsLinks);
        Objects.requireNonNull(initial, "initial");
    }

    /** 로고가 있는지. 없으면 첫 글자 표시가 로고 자리에 보인다. */
    public boolean hasLogo() {
        return logoUrl != null;
    }
}
