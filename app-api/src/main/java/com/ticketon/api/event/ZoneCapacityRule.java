package com.ticketon.api.event;

import com.ticketon.domain.event.ZoneCapacity;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 수량형 구역 정원 규칙 — 도메인 {@link ZoneCapacity}와 같은 규칙(1 이상)으로 본다({@link ZoneCapacity#isValid}). 비웠으면 통과한다 — 정원은
 * 수량형에만 있어 비워도 되는 칸이고, 구역 종류와 맞는지는 도메인이 본다(409).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ZoneCapacityRule.Validator.class)
public @interface ZoneCapacityRule {

    String message() default "정원은 1 이상으로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<ZoneCapacityRule, Integer> {

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || ZoneCapacity.isValid(value);
        }
    }
}
