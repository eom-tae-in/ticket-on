package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.SaveEventBookingSettingsCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.LeadTime;
import com.ticketon.domain.event.ReentrySetting;
import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.policy.CancellationFeeTier;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 위저드 5단계(공개 수준 · 오픈/종료 · 매수 제한 · 재입장 · 검표 시작 · 연령 제한 · 취소 규정 조정) 저장 요청 — 단계의 칸을 모두 보낸다(docs/03
 * "행사 만들기 위저드"). 칸 이름은 위저드 값 응답의 bookingSettings와 같다 — 받은 값을 고쳐 그대로 보내면 된다. 5단계는 전부 처음 값이 있어 건너뛸
 * 수 있다 — 건너뛰면 이 요청을 보내지 않고, 행사를 만들 때의 처음 값 그대로다.
 * <p>
 * 비울 수 있는 칸은 비운 것이 곧 처음 값이다 — 오픈 시각(판매 시작 즉시), 1인 총 매수(제한 없음), 연령 제한(없음). 나머지 칸은 필수다.
 * 예매 종료 · 검표 시작은 기준 시각(회차 시작 등)보다 앞서는 시간을 분으로 받는다 — 0이면 기준 시각 그대로다. 숫자의 범위와 취소 규정 구간의 모양은
 * 여기서 도메인 값 객체와 같은 규칙으로 본다(400). 기준보다 불리한 취소 규정, 걸린 적 없는 암호 보호는 409다.
 * <p>
 * 암호는 {@link #toString}에 싣지 않는다.
 *
 * @param visibility              공개 수준
 * @param newPassword             새로 정할 암호 — 암호 보호일 때만
 * @param bookingOpensAt          예매 오픈 시각
 * @param bookingCloseLeadMinutes 예매 종료 — 회차 시작보다 몇 분 앞에서 닫는지
 * @param checkinOpenLeadMinutes  검표 시작 — 회차 시작보다 몇 분 앞에서 여는지
 * @param maxSeatsPerSelection    1회 선택 매수
 * @param ticketsPerPersonLimit   1인 총 매수
 * @param sessionChoice           회차 선택 제약
 * @param reentryAllowed          재입장
 * @param minimumAge              연령 제한
 * @param cancellationFeeTiers    취소 규정 구간
 */
@Schema(description = "위저드 5단계(공개 수준 · 예매 설정 · 취소 규정 조정) 저장")
@PasswordOnlyWhenProtected
public record EventBookingSettingsRequest(
        @Schema(description = "공개 수준", requiredMode = REQUIRED)
        @NotNull(message = "공개 수준을 골라 주세요.")
        EventVisibilityCode visibility,

        @Schema(description = "새로 정할 암호 — 공개 수준이 암호 보호일 때만. 이미 암호가 걸려 있으면 비우거나 생략해 그대로 둔다(저장된 암호는 "
                + "보여 줄 수 없다). 걸린 적 없이 암호 보호로 두면서 비우면 409 EVENT_PASSWORD_REQUIRED. 암호 보호가 아닌데 적으면 400",
                example = "0315", requiredMode = NOT_REQUIRED, nullable = true)
        String newPassword,

        @Schema(description = "예매 오픈 시각(행사 단위). 비우면 판매를 시작하는 순간 연다", example = "2027-03-01T14:00:00+09:00",
                requiredMode = NOT_REQUIRED, nullable = true)
        OffsetDateTime bookingOpensAt,

        @Schema(description = "예매 종료 — 회차 시작(종일형은 마지막 입장)보다 몇 분 앞에서 닫는지. 0 이상. 처음 값 60", example = "60",
                requiredMode = REQUIRED)
        @NotNull(message = "예매 종료 시각을 입력해 주세요.")
        @Min(value = 0, message = "0분 이상으로 입력해 주세요.")
        Integer bookingCloseLeadMinutes,

        @Schema(description = "검표 시작 — 회차 시작(종일형은 운영 시작)보다 몇 분 앞에서 여는지. 0 이상. 처음 값 60", example = "60",
                requiredMode = REQUIRED)
        @NotNull(message = "검표 시작 시각을 입력해 주세요.")
        @Min(value = 0, message = "0분 이상으로 입력해 주세요.")
        Integer checkinOpenLeadMinutes,

        @Schema(description = "1회 선택 매수 — 한 번에 담을 수 있는 좌석 수. 1 이상. 처음 값 4", example = "4", requiredMode = REQUIRED)
        @NotNull(message = "1회 선택 매수를 입력해 주세요.")
        @Min(value = 1, message = "1석 이상으로 입력해 주세요.")
        Integer maxSeatsPerSelection,

        @Schema(description = "1인 총 매수. 비우면 제한 없음(처음 값)", requiredMode = NOT_REQUIRED, nullable = true)
        @Valid
        TicketsPerPersonLimitRequest ticketsPerPersonLimit,

        @Schema(description = "회차 선택 제약. 처음 값 NONE", requiredMode = REQUIRED)
        @NotNull(message = "회차 선택 제약을 골라 주세요.")
        SessionChoiceCode sessionChoice,

        @Schema(description = "재입장 — 켜면 같은 QR로 여러 번 들어온다. 처음 값 false", example = "false", requiredMode = REQUIRED)
        @NotNull(message = "재입장을 골라 주세요.")
        Boolean reentryAllowed,

        @Schema(description = "연령 제한 — 만 이 나이 이상만 예매한다. 1 이상. 비우면 제한 없음(처음 값)", example = "19",
                requiredMode = NOT_REQUIRED, nullable = true)
        @Min(value = 1, message = "연령 제한은 만 1세 이상으로 입력해 주세요.")
        Integer minimumAge,

        @ArraySchema(arraySchema = @Schema(description = "취소 규정 구간 — 관람일 당일(0일 전)부터 시작하는 구간이 있어야 하고 같은 날에서 "
                + "시작하는 구간은 둘일 수 없다. 순서는 상관없다. 기준(소비자분쟁해결기준)보다 관객에게 불리하면 409. 처음 값은 기준표 그대로",
                requiredMode = REQUIRED))
        @NotEmpty(message = "취소 규정 구간을 입력해 주세요.")
        @CancellationFeeTiersShape
        List<@NotNull(message = "취소 규정 구간을 입력해 주세요.") @Valid CancellationFeeTierRequest> cancellationFeeTiers) {

    /** 새 암호를 적었는지 — 비었거나 공백뿐이면 적지 않은 것이다. */
    boolean hasNewPassword() {
        return EventPassword.isValid(newPassword);
    }

    SaveEventBookingSettingsCommand toCommand(ActingStaff actingStaff, long eventId) {
        BookingSettings bookingSettings = new BookingSettings(
                bookingOpensAt == null ? null : bookingOpensAt.toInstant(),
                LeadTime.of(Duration.ofMinutes(bookingCloseLeadMinutes)),
                LeadTime.of(Duration.ofMinutes(checkinOpenLeadMinutes)),
                SeatsPerSelectionLimit.of(maxSeatsPerSelection),
                ticketsPerPersonLimit == null ? TicketsPerPersonLimit.unlimited() : ticketsPerPersonLimit.toLimit(),
                sessionChoice.toRestriction(),
                reentryAllowed ? ReentrySetting.ALLOWED : ReentrySetting.NOT_ALLOWED,
                minimumAge == null ? AgeRestriction.none() : AgeRestriction.atLeast(minimumAge));
        CancellationPolicy cancellationPolicy = CancellationPolicy.of(cancellationFeeTiers.stream()
                .map(CancellationFeeTierRequest::toTier)
                .toList());
        return new SaveEventBookingSettingsCommand(actingStaff, eventId, visibility.toEventVisibility(),
                hasNewPassword() ? new EventPassword(newPassword) : null, bookingSettings, cancellationPolicy);
    }

    @Override
    public String toString() {
        return "EventBookingSettingsRequest[visibility=" + visibility + ", newPassword=" + (newPassword == null ? null : "****")
                + ", bookingCloseLeadMinutes=" + bookingCloseLeadMinutes + ", maxSeatsPerSelection=" + maxSeatsPerSelection
                + ", cancellationFeeTiers=" + cancellationFeeTiers + "]";
    }

    /**
     * 1인 총 매수 — 한도와 세는 범위(docs/06 "1인 총 매수").
     *
     * @param maxTickets 한 사람이 그 범위에서 살 수 있는 장수
     * @param basis      세는 범위 — 날짜별 / 행사 전체
     */
    @Schema(description = "1인 총 매수 — 한도와 세는 범위")
    public record TicketsPerPersonLimitRequest(
            @Schema(description = "한도 — 1 이상", example = "4", requiredMode = REQUIRED)
            @NotNull(message = "1인 총 매수를 입력해 주세요.")
            @Min(value = 1, message = "1장 이상으로 입력해 주세요.")
            Integer maxTickets,

            @Schema(description = "세는 범위 — 날짜가 하나뿐인 행사는 둘이 같은 값이다", requiredMode = REQUIRED)
            @NotNull(message = "1인 총 매수를 세는 범위를 골라 주세요.")
            TicketsPerPersonBasisCode basis) {

        TicketsPerPersonLimit toLimit() {
            return TicketsPerPersonLimit.of(maxTickets, basis.toBasis());
        }
    }

    /**
     * 취소 규정 구간 하나 — "관람일 N일 전부터 공제율 P%"(docs/08 "법적 기준"). 다음 구간(더 가까운 날)이 시작하기 전날까지 이어진다.
     *
     * @param fromDaysBefore       이 구간이 시작하는 날 — 관람일 이만큼 전부터(0은 당일)
     * @param deductionRatePercent 공제율(%)
     */
    @Schema(description = "취소 규정 구간 — 관람일 N일 전부터 공제율 P%")
    public record CancellationFeeTierRequest(
            @Schema(description = "이 구간이 시작하는 날 — 관람일 이만큼 전부터. 0은 관람일 당일", example = "3", requiredMode = REQUIRED)
            @NotNull(message = "구간이 시작하는 날을 입력해 주세요.")
            @Min(value = 0, message = "0일 이상으로 입력해 주세요.")
            Integer fromDaysBefore,

            @Schema(description = "공제율(%) — 0~100", example = "0", requiredMode = REQUIRED)
            @NotNull(message = "공제율을 입력해 주세요.")
            @Min(value = 0, message = "공제율은 0~100 사이로 입력해 주세요.")
            @Max(value = 100, message = "공제율은 0~100 사이로 입력해 주세요.")
            Integer deductionRatePercent) {

        CancellationFeeTier toTier() {
            return CancellationFeeTier.of(fromDaysBefore, deductionRatePercent);
        }
    }
}
