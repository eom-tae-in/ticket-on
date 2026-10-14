package com.ticketon.api.account;

import com.ticketon.domain.account.NewPassword;
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
 * 새 비밀번호 최소 규칙 — 도메인 {@link NewPassword}와 같은 규칙({@value ProposedPolicy#PASSWORD_MINIMUM_LENGTH}자 이상, 확인 필요)으로 본다.
 * 빈 값도 규칙에 걸린다. 값이 아예 없으면 {@code @NotNull}이 맡는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NewPasswordRule.Validator.class)
public @interface NewPasswordRule {

    String message() default "비밀번호는 " + ProposedPolicy.PASSWORD_MINIMUM_LENGTH + "자 이상 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<NewPasswordRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || NewPassword.isValid(value);
        }
    }
}
