package com.ticketon.api.event;

import com.ticketon.domain.event.MinutePrecision;
import com.ticketon.domain.event.OperatingHours;
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
 * 운영 시간의 차례 — 운영 종료는 운영 시작보다 늦고, 마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않다. 도메인 {@link OperatingHours}와
 * 같은 규칙({@link OperatingHours#isValid}). 어긋난 칸(closeTime 또는 lastEntryTime)의 오류로 담는다. 운영 시작 · 종료가 다 있고 시각이 모두 분
 * 단위일 때만 본다 — 비었거나 분 단위가 아니면 그 칸의 규칙이 맡는다.
 * <p>
 * 자정을 넘겨 운영하는 날(18:00 ~ 02:00)은 받지 않는다 — 문서에 없다(확인 필요 #36, #135).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OperatingHoursOrder.Validator.class)
public @interface OperatingHoursOrder {

    String message() default "운영 종료는 운영 시작보다 늦게 입력해 주세요. 자정을 넘겨 운영하는 날은 아직 받지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<OperatingHoursOrder, OperatingHoursRequest> {

        static final String LAST_ENTRY_MESSAGE = "마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않게 입력해 주세요.";

        @Override
        public boolean isValid(OperatingHoursRequest request, ConstraintValidatorContext context) {
            if (request == null || request.openTime() == null || request.closeTime() == null || !allWholeMinutes(request)
                    || OperatingHours.isValid(request.openTime(), request.closeTime(), request.lastEntryTime())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            if (!request.closeTime().isAfter(request.openTime())) {
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("closeTime")
                        .addConstraintViolation();
                return false;
            }
            context.buildConstraintViolationWithTemplate(LAST_ENTRY_MESSAGE)
                    .addPropertyNode("lastEntryTime")
                    .addConstraintViolation();
            return false;
        }

        private static boolean allWholeMinutes(OperatingHoursRequest request) {
            return MinutePrecision.isWholeMinute(request.openTime()) && MinutePrecision.isWholeMinute(request.closeTime())
                    && (request.lastEntryTime() == null || MinutePrecision.isWholeMinute(request.lastEntryTime()));
        }
    }
}
