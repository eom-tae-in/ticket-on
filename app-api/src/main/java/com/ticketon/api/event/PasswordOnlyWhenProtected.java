package com.ticketon.api.event;

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
 * 새 암호는 암호 보호일 때만 받는다 — 5단계 요청의 공개 수준과 새 암호를 함께 본다. 걸리면 newPassword 칸의 오류로 담는다.
 * <p>
 * 말없이 버리지 않고 막는 까닭: 화면이 공개 수준을 잘못 보내 암호를 적었는데도 링크 전용으로 저장되면, 암호 없이 열리는 행사가 된다. 그런 어긋남을
 * 저장 전에 드러낸다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordOnlyWhenProtected.Validator.class)
public @interface PasswordOnlyWhenProtected {

    String message() default "암호는 공개 수준을 암호 보호로 고를 때만 입력합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<PasswordOnlyWhenProtected, EventBookingSettingsRequest> {

        @Override
        public boolean isValid(EventBookingSettingsRequest request, ConstraintValidatorContext context) {
            if (request == null || !request.hasNewPassword() || request.visibility() == null
                    || request.visibility() == EventVisibilityCode.PASSWORD_PROTECTED) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("newPassword")
                    .addConstraintViolation();
            return false;
        }
    }
}
