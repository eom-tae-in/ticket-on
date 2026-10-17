package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.EventPosterImage;
import java.util.Objects;

/**
 * 행사 포스터 올리기 입력.
 *
 * @param actingStaff 올리는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 * @param eventId     행사 id
 * @param image       포스터로 받을 수 있다고 확인한 그림(3:4 세로 · 용량 상한 · 받는 형식) — 화면의 크롭 도구로 잘라 올린 것
 */
public record UploadEventPosterCommand(ActingStaff actingStaff, long eventId, EventPosterImage image) {

    public UploadEventPosterCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(image, "image");
    }
}
