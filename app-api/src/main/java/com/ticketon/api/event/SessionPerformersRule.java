package com.ticketon.api.event;

import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.policy.QuantityPolicy;
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
 * 회차 출연 규칙 — 도메인 {@link SessionPerformers}와 같은 규칙({@link SessionPerformers#isAcceptable}): 한 줄(줄바꿈 없음), 앞뒤 공백을 걷고 NFC로
 * 맞춘 값이 {@value QuantityPolicy#SESSION_PERFORMERS_MAXIMUM_LENGTH}자 이하. 비웠거나 공백뿐이면 출연이 없는 것으로 받는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SessionPerformersRule.Validator.class)
public @interface SessionPerformersRule {

    String message() default "출연은 줄바꿈 없이 100자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<SessionPerformersRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return SessionPerformers.isAcceptable(value);
        }
    }
}
