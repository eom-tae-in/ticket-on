package com.ticketon.api.event;

import com.ticketon.domain.event.SessionPriceAdjustment;
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
 * 퍼센트 조정 규칙 — 도메인 {@link SessionPriceAdjustment}와 같은 규칙({@link SessionPriceAdjustment#isValidPercent}): -100%보다 크다. -100% 이하는
 * 어느 구역이든 0원 이하가 된다 — 돈을 받지 않는 표는 초대권이다. 비웠으면 종류와 맞는지의 규칙({@link PriceAdjustmentShape})이 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PriceAdjustmentPercentRule.Validator.class)
public @interface PriceAdjustmentPercentRule {

    String message() default "퍼센트 조정은 -100%보다 크게 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<PriceAdjustmentPercentRule, Integer> {

        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            return value == null || SessionPriceAdjustment.isValidPercent(value);
        }
    }
}
