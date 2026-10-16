package com.ticketon.api.event;

import com.ticketon.domain.event.EventCategoryName;
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
 * 카테고리 이름 · '기타' 장르명 길이 규칙 — 도메인 {@link EventCategoryName}과 같은 규칙(앞뒤 공백을 걷고 NFC로 맞춘 뒤
 * {@value ProposedPolicy#EVENT_CATEGORY_NAME_MAXIMUM_LENGTH}자 이하, 확인 필요)으로 본다({@link EventCategoryName#isValid}). 칸마다 화면에
 * 띄울 문구를 {@code message}로 적는다.
 * <p>
 * 너무 긴 장르명이 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다. 비었거나 공백뿐이면 통과한다 — 카테고리를 골랐는지는
 * {@code @FilledText}가, '기타'에 장르명이 있는지는 카테고리 규칙(409)이 본다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventCategoryNameRule.Validator.class)
public @interface EventCategoryNameRule {

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<EventCategoryNameRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || EventCategoryName.isValid(value);
        }
    }
}
