package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.ExternalLink;
import java.util.List;
import java.util.Objects;

/**
 * 주최자 공개 정보(소개 · SNS 링크) 편집 입력. 보낸 값으로 둘 다 바꾼다 — 로고는 따로 올리고 지운다.
 *
 * @param actingStaff  고치는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 * @param introduction 소개. 선택 — 비었거나 공백뿐이거나 null이면 소개를 지운다
 * @param snsLinks     SNS 링크 — 적은 순서대로. 없으면 빈 목록이고, null도 빈 목록으로 받는다
 */
public record ChangeOrganizerPublicProfileCommand(ActingStaff actingStaff, String introduction,
        List<ExternalLink> snsLinks) {

    public ChangeOrganizerPublicProfileCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        snsLinks = snsLinks == null ? List.of() : List.copyOf(snsLinks);
    }
}
