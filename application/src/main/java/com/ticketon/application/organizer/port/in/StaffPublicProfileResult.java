package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 운영진 프로필 — 공개되는 것만 담는다(docs/01 "운영진 프로필 — 보이는 것"). 실명 · 이메일 칸은 없다.
 *
 * @param nickname               닉네임
 * @param introduction           한 줄 소개. 적지 않았으면 null.
 * @param initial                닉네임 첫 글자 — 사진 대신 보인다
 * @param paletteIndex           사람마다 정해진 색의 팔레트 번호(0부터, 주최자 로고와 같은 8가지)
 * @param participatedOrganizers 참여한 주최자 — 소속을 시작한 순서
 */
public record StaffPublicProfileResult(String nickname, String introduction, String initial, int paletteIndex,
        List<ParticipatedOrganizer> participatedOrganizers) {

    public StaffPublicProfileResult {
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(initial, "initial");
        participatedOrganizers = List.copyOf(participatedOrganizers);
    }

    /**
     * 참여한 주최자 한 줄 — 이름 · 역할 · 기간.
     *
     * @param organizerId   주최자 id — 주최자 페이지로 갈 때 쓴다
     * @param organizerName 지금 주최자명
     * @param role          그 기간의 역할
     * @param startedAt     소속 시작 일시
     * @param endedAt       소속 종료 일시. 지금 소속돼 있으면 null.
     * @param activityEnded 활동 종료 — 그 주최자에서 나왔거나 주최자가 닫혔다
     */
    public record ParticipatedOrganizer(long organizerId, String organizerName, StaffRole role, Instant startedAt,
            Instant endedAt, boolean activityEnded) {

        public ParticipatedOrganizer {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(startedAt, "startedAt");
        }
    }
}
