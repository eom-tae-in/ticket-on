package com.ticketon.api.pricing;

import com.ticketon.domain.pricing.PricingErrorCode;
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
 * 할인 값이 조기예매 · 쿠폰으로 내놓을 수 있는 범위인지 — 정액 1원 이상, 정률 1% 이상 99% 이하(도메인 {@code Discount#isOfferable}과 같은 규칙,
 * docs/99 C "할인 값"). 범위 밖의 값이 안쪽까지 가면 금액 · 비율 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다. 걸리면 값 칸의
 * 오류로 담고, 문구는 도메인 오류({@link PricingErrorCode#DISCOUNT_OUT_OF_RANGE})와 같다. 비었으면 {@code @NotNull}이 맡는다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OfferableDiscount.Validator.class)
public @interface OfferableDiscount {

    String message() default "할인 금액은 1원 이상, 할인율은 1%에서 99% 사이로 정해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<OfferableDiscount, DiscountRequest> {

        private static final int HIGHEST_RATE = 99;

        @Override
        public boolean isValid(DiscountRequest request, ConstraintValidatorContext context) {
            if (request == null || request.method() == null || request.value() == null) {
                return true;
            }
            long value = request.value();
            boolean offerable = switch (request.method()) {
                case FIXED_AMOUNT -> value >= 1;
                case RATE -> value >= 1 && value <= HIGHEST_RATE;
            };
            if (!offerable) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()).addPropertyNode("value")
                        .addConstraintViolation();
            }
            return offerable;
        }
    }
}
