package com.ticketon.api.event;

import com.ticketon.domain.event.EventTitle;
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
 * 행사 제목 길이 규칙 — 도메인 {@link EventTitle}과 같은 규칙(앞뒤 공백을 걷고 NFC로 맞춘 뒤
 * {@value ProposedPolicy#EVENT_TITLE_MAXIMUM_LENGTH}자 이하, 확인 필요)으로 본다({@link EventTitle#isValid}). 글자 수는 사람이 세는 대로
 * 센다 — {@code @Size}는 이모지 하나를 두 자로 세고 공백을 걷기 전 길이를 보므로 쓰지 않는다.
 * <p>
 * 너무 긴 제목이 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다. 비었거나 공백뿐이면 {@code @FilledText}가 맡는다
 * (칸 하나에 오류 하나).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventTitleRule.Validator.class)
public @interface EventTitleRule {

    String message() default "제목은 " + ProposedPolicy.EVENT_TITLE_MAXIMUM_LENGTH + "자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<EventTitleRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || EventTitle.isValid(value);
        }
    }
}
