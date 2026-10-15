package com.ticketon.api.validation;

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
 * 꼭 적어야 하는 글 칸 — 앞뒤 공백을 걷어도 글자가 남아야 한다. 칸마다 화면에 띄울 문구를 {@code message}로 적는다.
 * <p>
 * 공백은 도메인 값 객체({@code OrganizerName} · {@code TelephoneNumber} · {@code SettlementAccount} ·
 * {@code OrganizerApplicationForm} · {@code EmailAddress})가 비었는지 볼 때와 같이 {@link String#strip()}으로 걷는다 — 전각 공백(U+3000)
 * 같은 유니코드 공백도 걷는다. {@code @NotBlank}는 U+0020 이하만 걷어 전각 공백만 넣은 칸을 통과시키고, 그 값은 안쪽에서 값 객체가
 * 만들어지지 않아 500이 되므로 쓰지 않는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FilledText.Validator.class)
public @interface FilledText {

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<FilledText, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value != null && !value.strip().isEmpty();
        }
    }
}
