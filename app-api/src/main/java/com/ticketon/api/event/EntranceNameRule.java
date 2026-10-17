package com.ticketon.api.event;

import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.policy.ProposedPolicy;
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
 * 입구 이름 길이 규칙 — 도메인 {@link EntranceName}과 같은 규칙(앞뒤 공백을 걷고 NFC로 맞춘 뒤
 * {@value ProposedPolicy#ENTRANCE_NAME_MAXIMUM_LENGTH}자 이하, 확인 필요)으로 본다({@link EntranceName#isValid}). 비었거나 공백뿐이면
 * {@code @FilledText}가 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EntranceNameRule.Validator.class)
public @interface EntranceNameRule {

    String message() default "입구 이름은 " + ProposedPolicy.ENTRANCE_NAME_MAXIMUM_LENGTH + "자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<EntranceNameRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || EntranceName.isValid(value);
        }
    }
}
