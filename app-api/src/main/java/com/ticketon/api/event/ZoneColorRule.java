package com.ticketon.api.event;

import com.ticketon.domain.event.ZoneColor;
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
 * 구역 색 꼴 규칙 — 도메인 {@link ZoneColor}와 같은 규칙(앞뒤 공백을 걷고 {@code #RRGGBB}, 대소문자는 가리지 않음)으로 본다
 * ({@link ZoneColor#isValid}). 비었거나 공백뿐이면 {@code @FilledText}가 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ZoneColorRule.Validator.class)
public @interface ZoneColorRule {

    String message() default "구역 색은 #RRGGBB 꼴로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<ZoneColorRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || ZoneColor.isValid(value);
        }
    }
}
