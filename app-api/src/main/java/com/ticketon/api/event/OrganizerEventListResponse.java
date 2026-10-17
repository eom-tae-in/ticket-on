package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.event.EventWizardResponse.EventWizardProgressResponse;
import com.ticketon.application.event.port.in.OrganizerEventListResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 행사 목록 — 작성 중 · 진행 중 · 지난 행사 세 구역(docs/13 "3. 주최 관리 — 내 행사 목록"). 화면 문구("마지막 수정 3일 전", "4/7 단계 · 가격
 * 설정까지 완료")는 화면이 만든다 — 여기는 시각 · 단계 · 날짜 값과 진행률 문구에 쓰는 단계 이름 · 번호를 준다.
 *
 * @param drafts     작성 중
 * @param inProgress 진행 중 행사
 * @param past       지난 행사
 */
@Schema(description = "내 행사 목록")
public record OrganizerEventListResponse(
        @ArraySchema(arraySchema = @Schema(description = "작성 중 — 목록 상단의 별도 구역. 마지막 수정이 최근인 것부터", requiredMode = REQUIRED))
        List<OrganizerDraftEventResponse> drafts,

        @ArraySchema(arraySchema = @Schema(description = "진행 중 행사 — 판매 대기 · 판매 중 · 판매 중단 · 판매 종료. 첫 날짜가 이른 것부터",
                requiredMode = REQUIRED))
        List<OrganizerListedEventResponse> inProgress,

        @ArraySchema(arraySchema = @Schema(description = "지난 행사 — 행사 종료 · 정산 완료 · 취소됨. 복제는 되고 편집은 안 된다. 마지막 날짜가 "
                + "늦은 것부터", requiredMode = REQUIRED))
        List<OrganizerListedEventResponse> past) {

    static OrganizerEventListResponse from(OrganizerEventListResult result) {
        return new OrganizerEventListResponse(result.drafts().stream().map(OrganizerDraftEventResponse::from).toList(),
                result.inProgress().stream().map(OrganizerListedEventResponse::from).toList(),
                result.past().stream().map(OrganizerListedEventResponse::from).toList());
    }

    /**
     * 작성 중 행사 한 장(docs/04 "작성 중 행사 관리") — 진행률, 마지막 수정일, 멈춘 단계로 바로 가는 버튼.
     *
     * @param eventId        행사 id
     * @param title          제목
     * @param posterUrl      포스터 주소
     * @param wizardProgress 작성 진행 단계
     * @param resumeStep     이어서 할 단계
     * @param lastModifiedAt 마지막 수정 시각
     * @param firstDate      일정의 첫 날짜
     * @param lastDate       일정의 마지막 날짜
     * @param folded         접어 둘지
     */
    @Schema(description = "작성 중 행사")
    public record OrganizerDraftEventResponse(
            @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
            long eventId,

            @Schema(description = "제목", example = "○○밴드 단독공연", requiredMode = REQUIRED)
            String title,

            @Schema(description = "포스터 주소 — 누구나 로그인 없이 여는 3:4 세로 그림의 절대 주소. 아직 올리지 않았으면 null",
                    example = "http://localhost:8080/api/public/files/event-posters/Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg",
                    requiredMode = NOT_REQUIRED, nullable = true)
            String posterUrl,

            @Schema(description = "작성 진행 단계 — 저장한 가장 뒤의 단계와 그 번호 · 단계 수. \"4/7 단계 · 가격 설정까지 완료\"의 4 · 7과 4단계",
                    requiredMode = REQUIRED)
            EventWizardProgressResponse wizardProgress,

            @Schema(description = "이어서 할 단계 — [이어서 작성]이 여는 단계. 저장한 가장 뒤의 단계의 바로 다음이고, 7단계(검토)면 그대로",
                    requiredMode = REQUIRED)
            EventWizardStepCode resumeStep,

            @Schema(description = "마지막 수정 시각(한국 시각) — 단계를 저장해 무엇이든 바뀐 시각. \"마지막 수정 3일 전\"은 화면이 만든다",
                    example = "2026-10-12T21:30:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime lastModifiedAt,

            @Schema(description = "일정의 첫 날짜(한국 날짜). 아직 날짜가 없으면 null", example = "2026-12-24", requiredMode = NOT_REQUIRED,
                    nullable = true)
            LocalDate firstDate,

            @Schema(description = "일정의 마지막 날짜(한국 날짜). 아직 날짜가 없으면 null", example = "2026-12-24", requiredMode = NOT_REQUIRED,
                    nullable = true)
            LocalDate lastDate,

            @Schema(description = "목록에서 접어 둘지 — 일정의 날짜가 모두 지났다(마지막 날짜가 오늘(한국 날짜) 전). 화면은 접어 두고 정리를 "
                    + "안내한다. 날짜가 없으면 false. 지우지는 않는다", example = "false", requiredMode = REQUIRED)
            boolean folded) {

        static OrganizerDraftEventResponse from(OrganizerEventListResult.DraftEvent draft) {
            return new OrganizerDraftEventResponse(draft.eventId(), draft.title(), draft.posterUrl(),
                    EventWizardProgressResponse.from(draft.completedStep()), EventWizardStepCode.from(draft.resumeStep()),
                    draft.lastModifiedAt().atZone(KoreanTime.ZONE).toOffsetDateTime(), draft.firstDate(), draft.lastDate(),
                    draft.folded());
        }
    }

    /**
     * 진행 중 · 지난 행사 한 장.
     *
     * @param eventId   행사 id
     * @param title     제목
     * @param posterUrl 포스터 주소
     * @param status    행사 상태
     * @param firstDate 일정의 첫 날짜
     * @param lastDate  일정의 마지막 날짜
     */
    @Schema(description = "진행 중 · 지난 행사")
    public record OrganizerListedEventResponse(
            @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
            long eventId,

            @Schema(description = "제목", example = "2026 봄 정기공연 〈열두 번째 달〉", requiredMode = REQUIRED)
            String title,

            @Schema(description = "포스터 주소 — 누구나 로그인 없이 여는 3:4 세로 그림의 절대 주소. 없으면 null",
                    example = "http://localhost:8080/api/public/files/event-posters/Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg",
                    requiredMode = NOT_REQUIRED, nullable = true)
            String posterUrl,

            @Schema(description = "행사 상태 — 저장된 상태", requiredMode = REQUIRED)
            EventStatusCode status,

            @Schema(description = "일정의 첫 날짜(한국 날짜). 날짜가 없으면 null", example = "2026-03-14", requiredMode = NOT_REQUIRED,
                    nullable = true)
            LocalDate firstDate,

            @Schema(description = "일정의 마지막 날짜(한국 날짜). 날짜가 없으면 null", example = "2026-03-15", requiredMode = NOT_REQUIRED,
                    nullable = true)
            LocalDate lastDate) {

        static OrganizerListedEventResponse from(OrganizerEventListResult.ListedEvent event) {
            return new OrganizerListedEventResponse(event.eventId(), event.title(), event.posterUrl(), EventStatusCode.from(event.status()),
                    event.firstDate(), event.lastDate());
        }
    }
}
