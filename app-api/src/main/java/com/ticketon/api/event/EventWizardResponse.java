package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.event.EventWizardStep;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 행사 위저드 값 — 행사 만들기 · 설정 편집 위저드가 저장된 단계의 칸을 채우고, 나갔다 들어왔을 때 이어서 할 단계를 안다(docs/03 "임시저장은
 * 자동으로", docs/04 "작성 중 행사 관리"). 행사 만들기 · 단계 저장 · 포스터 올리기 · 조회가 모두 이 모양을 돌려준다. 지금은 1단계(기본 정보 ·
 * 포스터)와 5단계(공개 수준 · 예매 설정 · 취소 규정)를 담는다 — 2 · 4단계는 그 기능이 칸을 더한다. 3단계의 구역과 6단계의 입구는 단계를 열 때
 * 따로 본다(GET …/zones, …/entrances) — 도면은 블록 · 좌석까지 커져(#43) 단계를 저장할 때마다 실을 까닭이 없다.
 * <p>
 * 공개 주소는 링크 전용 행사를 여는 자격이라 {@link #toString}에 싣지 않는다.
 *
 * @param eventId          행사 id
 * @param status           행사 상태
 * @param wizardProgress   작성 진행 단계
 * @param lastModifiedAt   마지막 수정 시각
 * @param publicAddress    공개 주소
 * @param posterUrl        포스터 주소
 * @param basicInformation 1단계 기본 정보
 * @param bookingSettings  5단계 공개 수준 · 예매 설정 · 취소 규정
 */
@Schema(description = "행사 위저드 값")
public record EventWizardResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @Schema(description = "행사 상태 — 위저드로 만드는 행사는 작성 중(DRAFT)이다", requiredMode = REQUIRED)
        EventStatusCode status,

        @Schema(description = "작성 진행 단계 — 작성 중에 저장한 가장 뒤의 단계. 앞 단계를 다시 저장해도 내려가지 않는다", requiredMode = REQUIRED)
        EventWizardProgressResponse wizardProgress,

        @Schema(description = "마지막 수정 시각(한국 시각) — 단계를 저장해 무엇이든 바뀐 시각. 같은 값을 다시 저장하면 그대로다",
                example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime lastModifiedAt,

        @Schema(description = "공개 주소 — 관객 링크에 들어가는 추측할 수 없는 URL 안전 문자 32자. 만들 때 정해지고 바뀌지 않는다. 링크 전용 행사는 "
                + "이 값을 아는 것이 곧 열 자격이라 필요한 곳에만 보인다", example = "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0", requiredMode = REQUIRED)
        String publicAddress,

        @Schema(description = "포스터 주소 — 누구나 로그인 없이 여는 3:4 세로 그림의 절대 주소. 1단계의 필수 항목이지만 행사를 만든 뒤 따로 올리므로 "
                + "아직 올리지 않았으면 null이다. 판매를 시작하기 전에 올려야 한다",
                example = "http://localhost:8080/api/public/files/event-posters/Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL.jpg",
                requiredMode = NOT_REQUIRED, nullable = true)
        String posterUrl,

        @Schema(description = "1단계 기본 정보", requiredMode = REQUIRED)
        EventBasicInformationResponse basicInformation,

        @Schema(description = "5단계 공개 수준 · 예매 설정 · 취소 규정 — 건너뛰었으면 처음 값", requiredMode = REQUIRED)
        EventBookingSettingsResponse bookingSettings) {

    static EventWizardResponse from(EventWizardResult result) {
        return new EventWizardResponse(result.eventId(), EventStatusCode.from(result.status()),
                EventWizardProgressResponse.from(result.wizardProgress()), koreanTime(result.lastModifiedAt()), result.publicAddress(),
                result.posterUrl(), EventBasicInformationResponse.from(result.basicInformation()),
                EventBookingSettingsResponse.from(result.bookingStep()));
    }

    @Override
    public String toString() {
        return "EventWizardResponse[eventId=" + eventId + ", status=" + status + ", wizardProgress=" + wizardProgress
                + ", publicAddress=****]";
    }

    private static OffsetDateTime koreanTime(Instant instant) {
        return instant == null ? null : instant.atZone(KoreanTime.ZONE).toOffsetDateTime();
    }

    /**
     * 작성 진행 단계 — 작성 중 행사 목록의 "4/7 단계 · 가격 설정까지 완료"와 [이어서 작성]이 쓴다.
     *
     * @param step       저장한 가장 뒤의 단계
     * @param stepNumber 그 단계의 번호 — 1부터
     * @param stepCount  위저드의 단계 수 — 7
     */
    @Schema(description = "작성 진행 단계")
    public record EventWizardProgressResponse(
            @Schema(description = "저장한 가장 뒤의 단계 — [이어서 작성]은 그다음 단계를 연다", requiredMode = REQUIRED)
            EventWizardStepCode step,

            @Schema(description = "그 단계의 번호 — 1부터", example = "1", requiredMode = REQUIRED)
            int stepNumber,

            @Schema(description = "위저드의 단계 수 — 진행률의 분모", example = "7", requiredMode = REQUIRED)
            int stepCount) {

        static EventWizardProgressResponse from(EventWizardStep step) {
            return new EventWizardProgressResponse(EventWizardStepCode.from(step), step.number(), EventWizardStep.stepCount());
        }
    }

    /**
     * 1단계 기본 정보 — 저장된 그대로(앞뒤 공백을 걷고 NFC로 맞춘 값). 요청(EventBasicInformationRequest)과 같은 칸 이름이다.
     *
     * @param title         제목
     * @param type          유형
     * @param categoryName  고른 카테고리 이름
     * @param genreName     '기타' 장르명
     * @param introduction  소개
     * @param venueName     공연장 이름
     * @param venueAddress  주소
     * @param venueDetails  상세 안내
     * @param mapLink       지도 링크
     * @param publicContact 공개용 연락처
     */
    @Schema(description = "1단계 기본 정보")
    public record EventBasicInformationResponse(
            @Schema(description = "제목", example = "2026 봄 정기공연 〈열두 번째 달〉", requiredMode = REQUIRED)
            String title,

            @Schema(description = "유형", requiredMode = REQUIRED)
            EventTypeCode type,

            @Schema(description = "고른 카테고리 이름 — 정식 카테고리 이름 또는 \"기타\"", example = "연극", requiredMode = REQUIRED)
            String categoryName,

            @Schema(description = "'기타'를 고르고 적은 장르명. 정식 카테고리면 null", example = "낭독극", requiredMode = NOT_REQUIRED,
                    nullable = true)
            String genreName,

            @Schema(description = "소개", example = "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", requiredMode = REQUIRED)
            String introduction,

            @Schema(description = "공연장 이름", example = "○○아트홀", requiredMode = REQUIRED)
            String venueName,

            @Schema(description = "주소", example = "서울특별시 종로구 대학로 12", requiredMode = REQUIRED)
            String venueAddress,

            @Schema(description = "상세 안내. 없으면 null", example = "지하 1층, 주차 불가", requiredMode = NOT_REQUIRED, nullable = true)
            String venueDetails,

            @Schema(description = "지도 링크. 없으면 null", example = "https://map.example/place/12", requiredMode = NOT_REQUIRED,
                    nullable = true)
            String mapLink,

            @Schema(description = "공개용 연락처 — 행사 페이지에 보인다. 없으면 null", example = "twelve-months@example.com",
                    requiredMode = NOT_REQUIRED, nullable = true)
            String publicContact) {

        static EventBasicInformationResponse from(EventWizardResult.BasicInformation information) {
            return new EventBasicInformationResponse(information.title(), EventTypeCode.from(information.type()),
                    information.categoryName(), information.genreName(), information.introduction(), information.venueName(),
                    information.venueAddress(), information.venueDetails(), information.mapLink(), information.publicContact());
        }

        @Override
        public String toString() {
            return "EventBasicInformationResponse[title=" + title + ", type=" + type + ", publicContact="
                    + (publicContact == null ? null : "****") + "]";
        }
    }

    /**
     * 5단계 — 공개 수준 · 예매 설정 · 취소 규정. 요청(EventBookingSettingsRequest)과 같은 칸 이름이다 — 암호만 다르다(응답은 걸려 있는지만).
     *
     * @param visibility                 공개 수준
     * @param passwordSet                암호가 걸려 있는지
     * @param bookingOpensAt             예매 오픈 시각
     * @param bookingCloseLeadMinutes    예매 종료 — 회차 시작보다 몇 분 앞
     * @param checkinOpenLeadMinutes     검표 시작 — 회차 시작보다 몇 분 앞
     * @param maxSeatsPerSelection       1회 선택 매수
     * @param ticketsPerPersonLimit      1인 총 매수
     * @param sessionChoice              회차 선택 제약
     * @param reentryAllowed             재입장
     * @param minimumAge                 연령 제한
     * @param cancellationFeeTiers       취소 규정 구간
     * @param cancellationPolicyStandard 취소 규정이 기준표 그대로인지
     */
    @Schema(description = "5단계 공개 수준 · 예매 설정 · 취소 규정")
    public record EventBookingSettingsResponse(
            @Schema(description = "공개 수준. 처음 값 PUBLIC", requiredMode = REQUIRED)
            EventVisibilityCode visibility,

            @Schema(description = "암호가 걸려 있는지 — 암호 보호일 때만 true. 저장된 암호는 보여 줄 수 없다", example = "false",
                    requiredMode = REQUIRED)
            boolean passwordSet,

            @Schema(description = "예매 오픈 시각(한국 시각). 비워 두었으면 null — 판매를 시작하는 순간 연다", example = "2027-03-01T14:00:00+09:00",
                    requiredMode = NOT_REQUIRED, nullable = true)
            OffsetDateTime bookingOpensAt,

            @Schema(description = "예매 종료 — 회차 시작(종일형은 마지막 입장)보다 몇 분 앞에서 닫는지. 처음 값 60", example = "60",
                    requiredMode = REQUIRED)
            int bookingCloseLeadMinutes,

            @Schema(description = "검표 시작 — 회차 시작(종일형은 운영 시작)보다 몇 분 앞에서 여는지. 처음 값 60", example = "60",
                    requiredMode = REQUIRED)
            int checkinOpenLeadMinutes,

            @Schema(description = "1회 선택 매수. 처음 값 4", example = "4", requiredMode = REQUIRED)
            int maxSeatsPerSelection,

            @Schema(description = "1인 총 매수. 제한이 없으면 null(처음 값)", requiredMode = NOT_REQUIRED, nullable = true)
            TicketsPerPersonLimitResponse ticketsPerPersonLimit,

            @Schema(description = "회차 선택 제약. 처음 값 NONE", requiredMode = REQUIRED)
            SessionChoiceCode sessionChoice,

            @Schema(description = "재입장을 켰는지. 처음 값 false", example = "false", requiredMode = REQUIRED)
            boolean reentryAllowed,

            @Schema(description = "연령 제한 — 만 이 나이 이상만 예매. 제한이 없으면 null(처음 값)", example = "19", requiredMode = NOT_REQUIRED,
                    nullable = true)
            Integer minimumAge,

            @ArraySchema(arraySchema = @Schema(description = "취소 규정 구간 — 먼 날부터. 겹치는 구간은 합쳐져 있다", requiredMode = REQUIRED))
            List<CancellationFeeTierResponse> cancellationFeeTiers,

            @Schema(description = "취소 규정이 기준표(소비자분쟁해결기준) 그대로인지", example = "true", requiredMode = REQUIRED)
            boolean cancellationPolicyStandard) {

        static EventBookingSettingsResponse from(EventWizardResult.BookingStep step) {
            TicketsPerPersonLimitResponse ticketsPerPerson = step.maxTicketsPerPerson() == null ? null
                    : new TicketsPerPersonLimitResponse(step.maxTicketsPerPerson(),
                            TicketsPerPersonBasisCode.from(step.ticketsPerPersonBasis()));
            return new EventBookingSettingsResponse(EventVisibilityCode.from(step.visibility()), step.passwordSet(),
                    koreanTime(step.bookingOpensAt()), Math.toIntExact(step.bookingCloseLeadTime().toMinutes()),
                    Math.toIntExact(step.checkinOpenLeadTime().toMinutes()), step.maxSeatsPerSelection(), ticketsPerPerson,
                    SessionChoiceCode.from(step.sessionChoice()), step.reentryAllowed(), step.minimumAge(),
                    step.cancellationFeeTiers().stream()
                            .map(tier -> new CancellationFeeTierResponse(tier.fromDaysBefore(), tier.deductionRatePercent()))
                            .toList(),
                    step.cancellationPolicyStandard());
        }
    }

    /**
     * 1인 총 매수.
     *
     * @param maxTickets 한도
     * @param basis      세는 범위
     */
    @Schema(description = "1인 총 매수 — 한도와 세는 범위")
    public record TicketsPerPersonLimitResponse(
            @Schema(description = "한도", example = "4", requiredMode = REQUIRED)
            int maxTickets,

            @Schema(description = "세는 범위", requiredMode = REQUIRED)
            TicketsPerPersonBasisCode basis) {
    }

    /**
     * 취소 규정 구간 하나.
     *
     * @param fromDaysBefore       이 구간이 시작하는 날 — 관람일 이만큼 전부터(0은 당일)
     * @param deductionRatePercent 공제율(%)
     */
    @Schema(description = "취소 규정 구간 — 관람일 N일 전부터 공제율 P%")
    public record CancellationFeeTierResponse(
            @Schema(description = "이 구간이 시작하는 날 — 관람일 이만큼 전부터. 0은 관람일 당일", example = "10", requiredMode = REQUIRED)
            int fromDaysBefore,

            @Schema(description = "공제율(%)", example = "0", requiredMode = REQUIRED)
            int deductionRatePercent) {
    }
}
