package com.ticketon.api.account;

import com.ticketon.domain.account.EmailAddress;
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
 * 이메일 주소 형식 — 도메인 {@link EmailAddress}와 같은 규칙('아이디@도메인' 모양, 254자 이하)으로 본다.
 * 비었거나 공백뿐이면 통과한다 — 빈 칸은 {@code @FilledText}가 맡는다(칸 하나에 오류 하나).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailAddressFormat.Validator.class)
public @interface EmailAddressFormat {

    String message() default "이메일 주소를 확인해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<EmailAddressFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || EmailAddress.isValid(value);
        }
    }
}
