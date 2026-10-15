package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.domain.account.InitialAvatar;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerPublicProfile;

/** 주최자의 공개 정보를 Result로 옮긴다 — 로고 열쇠는 파일 저장소의 공개 주소로 바꾸고, 첫 글자 표시를 함께 담는다. */
final class OrganizerPublicProfileResults {

    private OrganizerPublicProfileResults() {
    }

    static OrganizerPublicProfileResult of(Organizer organizer, OrganizerLogoStoragePort logoStorage) {
        OrganizerPublicProfile profile = organizer.publicProfile();
        String logoUrl = profile.hasLogo() ? logoStorage.publicUrlOf(profile.logo()) : null;
        InitialAvatar avatar = organizer.initialAvatar();
        return new OrganizerPublicProfileResult(organizer.id().orElseThrow(), profile.introduction(),
                profile.snsLinks().stream().map(ExternalLink::value).toList(), logoUrl, avatar.initial(),
                avatar.paletteIndex());
    }
}
