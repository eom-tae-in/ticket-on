package com.ticketon.api.pricing;

import com.ticketon.domain.policy.QuantityPolicy;
import com.ticketon.domain.pricing.CouponCode;
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
 * 쿠폰 코드 규칙 — 도메인 {@link CouponCode}와 같은 규칙(앞뒤 공백을 걷고 NFC · 대문자로 맞춘 뒤, 안에 공백 없이
 * {@value QuantityPolicy#COUPON_CODE_MAXIMUM_LENGTH}자 이하)으로 본다({@link CouponCode#isValid}). 어긋난 코드가 안쪽까지 가면 값 객체가
 * 만들어지지 않아 500이 되므로 여기서 400으로 막는다. 비었거나 공백뿐이면 {@code @FilledText}가 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CouponCodeRule.Validator.class)
public @interface CouponCodeRule {

    String message() default "쿠폰 코드는 공백 없이 " + QuantityPolicy.COUPON_CODE_MAXIMUM_LENGTH + "자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<CouponCodeRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || CouponCode.isValid(value);
        }
    }
}
