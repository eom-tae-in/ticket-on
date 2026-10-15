package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.InitialAvatarResponse;
import com.ticketon.application.organizer.port.in.StaffPublicProfileResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 운영진 프로필 — 공개 페이지(docs/01 "공개 페이지 — 운영진 프로필", docs/13 "운영진 프로필"). 닉네임 · 한 줄 소개 · 첫 글자 표시 · 참여한
 * 주최자만 담는다. <b>실명 · 이메일 칸은 두지 않는다</b> — 로그인 없이 누구나 보는 응답이다.
 *
 * @param nickname               닉네임
 * @param introduction           한 줄 소개. 적지 않았으면 null.
 * @param avatar                 사진 대신 보이는 첫 글자 표시
 * @param participatedOrganizers 참여한 주최자 — 소속을 시작한 순서
 */
@Schema(description = "운영진 프로필 — 공개")
public record StaffPublicProfileResponse(
        @Schema(description = "닉네임 — 실명 대신 보인다", example = "조용한고래", requiredMode = REQUIRED)
        String nickname,

        @Schema(description = "한 줄 소개. 적지 않았으면 null", example = "극단에서 연출하고 있습니다", requiredMode = NOT_REQUIRED,
                nullable = true)
        String introduction,

        @Schema(description = "사진 대신 보이는 첫 글자 표시", requiredMode = REQUIRED)
        InitialAvatarResponse avatar,

        @Schema(description = "참여한 주최자 — 소속 한 건이 한 줄, 소속을 시작한 순서. 역할이 바뀌었으면 기간마다 한 줄. 나왔거나 닫힌 주최자도 "
                + "활동 종료로 남고, 영구 정지된 주최자는 빠진다. 없으면 빈 목록", requiredMode = REQUIRED)
        List<ParticipatedOrganizer> participatedOrganizers) {

    static StaffPublicProfileResponse from(StaffPublicProfileResult result) {
        return new StaffPublicProfileResponse(result.nickname(), result.introduction(),
                new InitialAvatarResponse(result.initial(), result.paletteIndex()),
                result.participatedOrganizers().stream()
                        .map(participation -> new ParticipatedOrganizer(participation.organizerId(),
                                participation.organizerName(), StaffRoleCode.from(participation.role()),
                                koreanTime(participation.startedAt()),
                                participation.endedAt() == null ? null : koreanTime(participation.endedAt()),
                                participation.activityEnded()))
                        .toList());
    }

    /**
     * 참여한 주최자 한 줄 — 이름 · 역할 · 기간. 누르면 주최자 페이지로 간다.
     *
     * @param organizerId   주최자 id
     * @param organizerName 지금 주최자명
     * @param role          그 기간의 역할
     * @param startedAt     소속 시작 일시 — 한국 시각
     * @param endedAt       소속 종료 일시 — 한국 시각. 지금 소속돼 있으면 null.
     * @param activityEnded 활동 종료 — 그 주최자에서 나왔거나 주최자가 닫혔다
     */
    @Schema(description = "참여한 주최자 한 줄 — 이름 · 역할 · 기간")
    public record ParticipatedOrganizer(
            @Schema(description = "주최자 id — 주최자 페이지로 갈 때 쓴다", example = "12", requiredMode = REQUIRED)
            long organizerId,

            @Schema(description = "지금 주최자명", example = "극단 열두달", requiredMode = REQUIRED)
            String organizerName,

            @Schema(description = "그 기간의 역할", requiredMode = REQUIRED)
            StaffRoleCode role,

            @Schema(description = "소속 시작 일시 — 한국 시각(+09:00). 화면은 기간을 연도로 보인다(예: 2019~)",
                    example = "2019-03-04T10:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime startedAt,

            @Schema(description = "소속 종료 일시 — 한국 시각(+09:00). 지금 소속돼 있으면 null", example = "2024-11-30T18:00:00+09:00",
                    requiredMode = NOT_REQUIRED, nullable = true)
            OffsetDateTime endedAt,

            @Schema(description = "활동 종료 — 그 주최자에서 나왔거나 주최자가 닫혔다. 화면은 \"활동 종료\"로 보인다. 역할만 바뀌어 지금도 "
                    + "소속돼 있으면 false", example = "false", requiredMode = REQUIRED)
            boolean activityEnded) {
    }
}
