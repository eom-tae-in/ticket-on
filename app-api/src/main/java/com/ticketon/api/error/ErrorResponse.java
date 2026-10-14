package com.ticketon.api.error;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 모든 오류 응답의 본문. 화면은 {@code code}로 분기하고 {@code message}를 그대로 띄운다.
 * <p>
 * 화면 타입이 이 모양에서 생성되므로 칸은 모두 필수로 명세에 싣는다(입력 오류가 아니어도 fieldErrors는 빈 배열로 온다).
 *
 * @param code        오류 코드 — 도메인 오류 코드의 이름(예: {@code SEAT_ALREADY_TAKEN}) 또는 {@link ApiErrorCode}
 * @param message     사용자에게 보여 줄 문구
 * @param fieldErrors 입력 검증에 걸린 칸. 입력 오류가 아니면 빈 배열
 */
@Schema(description = "오류 응답. 화면은 code로 분기하고 message를 그대로 띄운다.")
public record ErrorResponse(
        @Schema(description = "오류 코드. 한 번 내보낸 코드는 바꾸지 않는다.", example = "SEAT_ALREADY_TAKEN", requiredMode = REQUIRED)
        String code,
        @Schema(description = "사용자에게 그대로 보여 줄 문구", example = "방금 다른 분이 선택했습니다", requiredMode = REQUIRED)
        String message,
        @Schema(description = "입력 검증에 걸린 칸. 입력 오류가 아니면 빈 배열", requiredMode = REQUIRED)
        List<FieldError> fieldErrors) {

    public ErrorResponse {
        fieldErrors = List.copyOf(fieldErrors);
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.code(), errorCode.message(), fieldErrors);
    }

    /**
     * 입력 검증에 걸린 칸 하나.
     *
     * @param field   칸 이름 — Request의 칸 이름 그대로(중첩은 {@code seats[0].row}처럼)
     * @param message 그 칸 옆에 보여 줄 문구
     */
    @Schema(description = "입력 검증에 걸린 칸 하나")
    public record FieldError(
            @Schema(description = "칸 이름. Request의 칸 이름 그대로이고, 중첩은 seats[0].row처럼 쓴다.", example = "name",
                    requiredMode = REQUIRED)
            String field,
            @Schema(description = "그 칸 옆에 보여 줄 문구", example = "이름을 입력해 주세요", requiredMode = REQUIRED)
            String message) {
    }
}
