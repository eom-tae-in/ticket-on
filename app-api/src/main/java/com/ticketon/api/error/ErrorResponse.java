package com.ticketon.api.error;

import com.ticketon.domain.error.ErrorCode;
import java.util.List;

/**
 * 모든 오류 응답의 본문. 화면은 {@code code}로 분기하고 {@code message}를 그대로 띄운다.
 *
 * @param code        오류 코드 — 도메인 오류 코드의 이름(예: {@code SEAT_ALREADY_TAKEN}) 또는 {@link ApiErrorCode}
 * @param message     사용자에게 보여 줄 문구
 * @param fieldErrors 입력 검증에 걸린 칸. 입력 오류가 아니면 빈 배열
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

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
    public record FieldError(String field, String message) {
    }
}
