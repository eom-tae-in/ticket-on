package com.ticketon.api.event;

import com.ticketon.domain.event.MinutePrecision;
import com.ticketon.domain.event.TimedSessionTime;
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
 * 회차형 회차의 종료 시각은 시작 시각보다 늦다 — 도메인 {@link TimedSessionTime}과 같은 규칙({@link TimedSessionTime#isValid}). 걸리면 endTime 칸의
 * 오류로 담는다. 시작 · 종료가 다 있고 둘 다 분 단위일 때만 본다 — 비었거나 분 단위가 아니면 그 칸의 규칙이 맡는다.
 * <p>
 * 자정을 넘겨 끝나는 회차(22:00 시작 ~ 00:30 종료)는 받지 않는다 — 문서에 없어 종료가 다음 날임을 적을 길이 없다(확인 필요 #36, #135).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SessionTimeOrder.Validator.class)
public @interface SessionTimeOrder {

    String message() default "종료 시각은 시작 시각보다 늦게 입력해 주세요. 자정을 넘겨 끝나는 회차는 아직 받지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<SessionTimeOrder, SessionTimeFields> {

        @Override
        public boolean isValid(SessionTimeFields request, ConstraintValidatorContext context) {
            if (request == null || request.startTime() == null || request.endTime() == null
                    || !MinutePrecision.isWholeMinute(request.startTime()) || !MinutePrecision.isWholeMinute(request.endTime())
                    || TimedSessionTime.isValid(request.startTime(), request.endTime())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("endTime")
                    .addConstraintViolation();
            return false;
        }
    }
}
