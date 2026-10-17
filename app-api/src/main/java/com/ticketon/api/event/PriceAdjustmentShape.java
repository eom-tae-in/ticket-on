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
 * 회차 가격 조정 요청의 칸이 종류와 맞는지 — 금액은 금액 칸만, 퍼센트는 퍼센트 칸만 있고, 조정 없음은 둘 다 없다. 걸리면 빠졌거나 남는 칸의
 * 오류로 담는다. 퍼센트의 범위(-100%보다 큼)는 퍼센트 칸의 규칙({@link PriceAdjustmentPercentRule})이 맡는다.
 * <p>
 * 남는 칸을 말없이 버리지 않는 까닭: 화면이 종류를 잘못 보내 "+10,000"을 적었는데 조정 없음으로 저장되면 주최자가 넣은 가격이 사라진다.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PriceAdjustmentShape.Validator.class)
public @interface PriceAdjustmentShape {

    String message() default "조정 종류에 맞는 값만 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<PriceAdjustmentShape, SessionPriceAdjustmentRequest> {

        static final String AMOUNT_REQUIRED = "조정 금액을 입력해 주세요.";
        static final String PERCENT_REQUIRED = "조정 퍼센트를 입력해 주세요.";

        @Override
        public boolean isValid(SessionPriceAdjustmentRequest request, ConstraintValidatorContext context) {
            if (request == null || request.kind() == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            String extra = context.getDefaultConstraintMessageTemplate();
            switch (request.kind()) {
                case NONE -> {
                    valid &= absent(request.amount(), "amount", extra, context);
                    valid &= absent(request.percent(), "percent", extra, context);
                }
                case AMOUNT -> {
                    valid &= present(request.amount(), "amount", AMOUNT_REQUIRED, context);
                    valid &= absent(request.percent(), "percent", extra, context);
                }
                case PERCENT -> {
                    valid &= absent(request.amount(), "amount", extra, context);
                    valid &= present(request.percent(), "percent", PERCENT_REQUIRED, context);
                }
            }
            return valid;
        }

        private static boolean present(Object value, String field, String message, ConstraintValidatorContext context) {
            if (value != null) {
                return true;
            }
            context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
            return false;
        }

        private static boolean absent(Object value, String field, String message, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
            return false;
        }
    }
}
