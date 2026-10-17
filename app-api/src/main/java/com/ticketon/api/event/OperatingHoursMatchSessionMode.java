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
 * 기본 운영 시간은 종일형일 때만, 종일형이면 꼭 받는다 — 회차 방식 선택 요청의 회차 방식과 기본 운영 시간을 함께 본다. 걸리면
 * defaultOperatingHours 칸의 오류로 담는다.
 * <p>
 * 회차형에 적은 운영 시간을 말없이 버리지 않고 막는 까닭: 화면이 회차 방식을 잘못 보내 운영 시간을 적었는데도 회차형으로 저장되면 주최자가 넣은
 * 운영 시간이 사라진다. 그런 어긋남을 저장 전에 드러낸다(5단계의 {@link PasswordOnlyWhenProtected}와 같다).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OperatingHoursMatchSessionMode.Validator.class)
public @interface OperatingHoursMatchSessionMode {

    String message() default "종일형은 기본 운영 시간을 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<OperatingHoursMatchSessionMode, ChooseSessionModeRequest> {

        static final String ONLY_FOR_ALL_DAY_MESSAGE = "기본 운영 시간은 종일형을 고를 때만 입력합니다.";

        @Override
        public boolean isValid(ChooseSessionModeRequest request, ConstraintValidatorContext context) {
            if (request == null || request.sessionMode() == null) {
                return true;
            }
            boolean allDay = request.sessionMode() == SessionModeCode.ALL_DAY;
            boolean hasHours = request.defaultOperatingHours() != null;
            if (allDay == hasHours) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(allDay ? context.getDefaultConstraintMessageTemplate() : ONLY_FOR_ALL_DAY_MESSAGE)
                    .addPropertyNode("defaultOperatingHours")
                    .addConstraintViolation();
            return false;
        }
    }
}
