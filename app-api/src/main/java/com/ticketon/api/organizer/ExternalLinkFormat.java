package com.ticketon.api.organizer;

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
 * 홈페이지·SNS 링크 형식 — 도메인 {@link ExternalLink}와 같은 규칙(http 또는 https의 절대 주소)으로 본다. 목록의 칸 하나하나에 붙인다.
 * 빈 칸도 주소가 아니므로 걸린다 — 링크가 없으면 칸을 두지 않는다.
 * <p>
 * 형식이 틀린 링크가 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExternalLinkFormat.Validator.class)
public @interface ExternalLinkFormat {

    String message() default "http:// 또는 https://로 시작하는 주소를 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<ExternalLinkFormat, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return ExternalLink.isValid(value);
        }
    }
}
