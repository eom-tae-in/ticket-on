package com.ticketon.api.pricing;

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
 * 할인 기간의 끝이 시작보다 뒤인지 — 도메인 {@code DiscountPeriod}와 같은 규칙. 어긋난 기간이 안쪽까지 가면 값 객체가 만들어지지 않아 500이
 * 되므로 여기서 400으로 막고, 끝 칸의 오류로 담는다. 비었으면 {@code @NotNull}이 맡는다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PeriodOrder.Validator.class)
public @interface PeriodOrder {

    String message() default "끝 시각을 시작 시각보다 뒤로 정해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<PeriodOrder, DiscountPeriodRequest> {

        @Override
        public boolean isValid(DiscountPeriodRequest request, ConstraintValidatorContext context) {
            if (request == null || request.startsAt() == null || request.endsAt() == null) {
                return true;
            }
            boolean ordered = request.startsAt().toInstant().isBefore(request.endsAt().toInstant());
            if (!ordered) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()).addPropertyNode("endsAt")
                        .addConstraintViolation();
            }
            return ordered;
        }
    }
}
