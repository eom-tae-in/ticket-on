package com.ticketon.api.account;

import com.ticketon.domain.account.PhoneNumber;
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
 * 관객 전화번호 형식 — 도메인 {@link PhoneNumber}와 같은 규칙(숫자만 남겨 010으로 시작하는 11자리)으로 본다.
 * 형식이 틀린 번호가 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다. 비어 있으면 {@code @NotBlank}가 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneNumberFormat.Validator.class)
public @interface PhoneNumberFormat {

    String message() default "010으로 시작하는 휴대폰 번호 11자리를 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<PhoneNumberFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || PhoneNumber.isValid(value);
        }
    }
}
