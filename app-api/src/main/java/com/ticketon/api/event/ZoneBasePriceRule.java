package com.ticketon.api.event;

import com.ticketon.domain.event.ZoneBasePrice;
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
 * 구역 기본가 규칙 — 도메인 {@link ZoneBasePrice}와 같은 규칙(1원 이상)으로 본다({@link ZoneBasePrice#isValid}). 돈을 받지 않는 표는
 * 초대권이다. 비웠으면 {@code @NotNull}이 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ZoneBasePriceRule.Validator.class)
public @interface ZoneBasePriceRule {

    String message() default "기본가는 1원 이상으로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<ZoneBasePriceRule, Long> {

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            return value == null || ZoneBasePrice.isValid(value);
        }
    }
}
