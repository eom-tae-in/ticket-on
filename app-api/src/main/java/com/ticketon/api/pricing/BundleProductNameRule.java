package com.ticketon.api.pricing;

import com.ticketon.domain.policy.ProposedPolicy;
import com.ticketon.domain.pricing.BundleProductName;
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
 * 묶음권 이름 길이 규칙 — 도메인 {@link BundleProductName}과 같은 규칙(앞뒤 공백을 걷고 NFC로 맞춘 뒤
 * {@value ProposedPolicy#BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH}자 이하, 확인 필요 #223)으로 본다. 너무 긴 이름이 안쪽까지 가면 값 객체가 만들어지지
 * 않아 500이 되므로 여기서 400으로 막는다. 비었거나 공백뿐이면 {@code @FilledText}가 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BundleProductNameRule.Validator.class)
public @interface BundleProductNameRule {

    String message() default "묶음권 이름은 " + ProposedPolicy.BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH + "자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<BundleProductNameRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || BundleProductName.isValid(value);
        }
    }
}
