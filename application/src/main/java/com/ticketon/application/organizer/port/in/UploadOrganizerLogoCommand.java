package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.OrganizerLogoImage;
import java.util.Objects;

/**
 * 주최자 로고 올리기 입력.
 *
 * @param actingStaff 올리는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 * @param image       로고로 받을 수 있다고 확인한 그림(정사각형 · 용량 상한 · 받는 형식) — 화면의 크롭 도구로 잘라 올린 것
 */
public record UploadOrganizerLogoCommand(ActingStaff actingStaff, OrganizerLogoImage image) {

    public UploadOrganizerLogoCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(image, "image");
    }
}
