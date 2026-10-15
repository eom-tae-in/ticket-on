package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimCommand;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소유자 이전 신청 요청 — 소유자와 연락이 끊긴 사정(docs/01 "소유자 넘기기 — 소유자와 연락이 끊긴 경우").
 * <p>
 * 비었는지는 도메인 {@link OwnershipClaimReason}과 같이 앞뒤 공백을 {@link String#strip()}으로 걷어 본다({@link FilledText}).
 *
 * @param reason 신청 사유
 */
@Schema(description = "소유자 이전 신청 요청")
public record SubmitOwnershipClaimRequest(
        @Schema(description = "신청 사유 — 소유자와 연락이 끊긴 사정. 관리자는 이 글과 주최자 신청 때 받은 연락처 · SNS로 확인한다",
                example = "전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다. 올해 정기공연을 준비해야 합니다.", requiredMode = REQUIRED)
        @FilledText(message = "신청 사유를 입력해 주세요.")
        String reason) {

    SubmitOwnershipClaimCommand toCommand(ActingStaff actingStaff) {
        return new SubmitOwnershipClaimCommand(actingStaff, OwnershipClaimReason.of(reason));
    }
}
