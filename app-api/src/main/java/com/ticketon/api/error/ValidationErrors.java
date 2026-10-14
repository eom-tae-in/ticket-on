package com.ticketon.api.error;

import com.ticketon.api.error.ErrorResponse.FieldError;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 입력 검증 결과를 {@link FieldError}(칸 이름·문구) 목록으로 바꾼다.
 * <p>
 * 문구는 Bean Validation 제약의 문구를 그대로 쓴다. Request의 제약에는 화면에 띄울 문구를 직접 적는다
 * ({@code @NotBlank(message = "이름을 입력해 주세요")}). 적지 않으면 Hibernate Validator의 한국어 기본 문구가 나간다.
 * 형식이 틀려 값을 바꾸지 못한 칸은 내부 사정(자바 타입 이름 등)이 섞인 문구 대신 {@link #INVALID_FORMAT}을,
 * 빠진 파라미터는 {@link #REQUIRED}를 쓴다.
 */
final class ValidationErrors {

    static final String INVALID_FORMAT = "형식이 올바르지 않습니다.";

    static final String REQUIRED = "필수 항목입니다.";

    private ValidationErrors() {
    }

    /**
     * {@code @Valid} 본문·{@code @ModelAttribute}의 검증 결과.
     * 칸 하나에 걸리지 않는 검증(두 칸을 함께 보는 규칙)은 칸 이름 대신 Request 이름으로 담는다.
     * 화면에서 칸 옆에 띄우려면 검증기에서 칸을 지정한다.
     */
    static List<FieldError> of(Errors errors) {
        List<FieldError> fieldErrors = new ArrayList<>();
        for (org.springframework.validation.FieldError error : errors.getFieldErrors()) {
            fieldErrors.add(new FieldError(error.getField(), messageOf(error)));
        }
        for (ObjectError error : errors.getGlobalErrors()) {
            fieldErrors.add(new FieldError(error.getObjectName(), messageOf(error)));
        }
        return fieldErrors;
    }

    /** 파라미터 제약({@code @RequestParam @Min(1) int quantity} 등)의 메서드 검증 결과. */
    static List<FieldError> of(HandlerMethodValidationException exception) {
        List<FieldError> fieldErrors = new ArrayList<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                fieldErrors.addAll(of(parameterErrors));
                continue;
            }
            String parameterName = parameterName(result.getMethodParameter());
            result.getResolvableErrors()
                    .forEach(error -> fieldErrors.add(new FieldError(parameterName, error.getDefaultMessage())));
        }
        return fieldErrors;
    }

    /** 형식이 틀려 값을 바꾸지 못한 칸 하나. */
    static List<FieldError> invalidFormat(String field) {
        return List.of(new FieldError(field, INVALID_FORMAT));
    }

    /** 꼭 있어야 하는데 빠진 칸 하나. */
    static List<FieldError> missing(String field) {
        return List.of(new FieldError(field, REQUIRED));
    }

    private static String messageOf(ObjectError error) {
        if (error instanceof org.springframework.validation.FieldError fieldError && fieldError.isBindingFailure()) {
            return INVALID_FORMAT;
        }
        return error.getDefaultMessage();
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }
}
