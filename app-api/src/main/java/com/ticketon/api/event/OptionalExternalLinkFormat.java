package com.ticketon.api.event;

import com.ticketon.domain.organizer.ExternalLink;
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
 * 선택 칸인 외부 링크(지도 링크 등)의 형식 — 적었으면 도메인 {@link ExternalLink}와 같은 규칙(http 또는 https의 절대 주소)으로 본다
 * ({@link ExternalLink#isValid}). 비었거나 공백뿐이면 적지 않은 것으로 통과한다 — 목록의 칸마다 붙여 빈 칸도 막는
 * {@code api.organizer.ExternalLinkFormat}과 다르다.
 * <p>
 * 형식이 틀린 링크가 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OptionalExternalLinkFormat.Validator.class)
public @interface OptionalExternalLinkFormat {

    String message() default "http:// 또는 https://로 시작하는 주소를 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<OptionalExternalLinkFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || ExternalLink.isValid(value);
        }
    }
}
