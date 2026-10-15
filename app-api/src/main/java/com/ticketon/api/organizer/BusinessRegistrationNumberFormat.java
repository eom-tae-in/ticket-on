package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.BusinessRegistrationNumber;
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
 * 사업자등록번호 형식 — 도메인 {@link BusinessRegistrationNumber}와 같은 규칙(하이픈·공백을 걷은 숫자 10자리)으로 본다.
 * <p>
 * 비어 있으면 통과한다 — 사업자에게 빠진 항목인지는 신청 규칙이 사업자 여부와 함께 본다(409 BUSINESS_INFORMATION_MISSING).
 * 형식이 틀린 번호가 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BusinessRegistrationNumberFormat.Validator.class)
public @interface BusinessRegistrationNumberFormat {

    String message() default "사업자등록번호 숫자 10자리를 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<BusinessRegistrationNumberFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || BusinessRegistrationNumber.isValid(value);
        }
    }
}
