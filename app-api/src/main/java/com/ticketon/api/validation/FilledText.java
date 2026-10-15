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
 * 공백은 도메인 값 객체({@code EmailAddress} · {@code Nickname} · 가입 정보의 이름 · {@code OrganizerName} · {@code TelephoneNumber} ·
 * {@code SettlementAccount} · {@code OrganizerApplicationForm} 등)가 비었는지 볼 때와 같이 {@link String#strip()}으로 걷는다 — 전각
 * 공백(U+3000) 같은 유니코드 공백도 걷는다. 도메인 값 객체로 넘어가는 칸에는 {@code @NotBlank} 대신 이것을 쓴다. {@code @NotBlank}가 무엇을
 * 공백으로 보는지는 검증 구현에 달렸다(Hibernate Validator 8은 U+0020 이하만 걷는 {@link String#trim()}, 9는 {@link String#isBlank()}).
 * 기준이 도메인과 어긋나면 전각 공백만 넣은 칸이 검증을 통과하고, 안쪽에서 값 객체가 만들어지지 않아 400이 아니라 500이 된다.
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
