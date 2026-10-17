package com.ticketon.api.event;

import com.ticketon.domain.event.MinutePrecision;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalTime;

/**
 * 일정의 시각은 분 단위만 받는다 — 도메인 {@link MinutePrecision}과 같은 규칙. 초나 그 아래가 0이 아닌 시각("19:00:30")은 조용히 자르지 않고
 * 400으로 막는다(확인 필요 #41 — 저장 뒤 다시 읽은 값이 보낸 값과 달라지지 않게). 비웠으면 {@code @NotNull}이 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WholeMinute.Validator.class)
public @interface WholeMinute {

    String message() default "시각은 분 단위로 입력해 주세요. 예: 19:00";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<WholeMinute, LocalTime> {

        @Override
        public boolean isValid(LocalTime value, ConstraintValidatorContext context) {
            return value == null || MinutePrecision.isWholeMinute(value);
        }
    }
}
