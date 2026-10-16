package com.ticketon.api.event;

import com.ticketon.api.event.EventBookingSettingsRequest.CancellationFeeTierRequest;
import com.ticketon.domain.event.CancellationPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 취소 규정 구간 목록의 모양 — 도메인 {@link CancellationPolicy}가 받는 모양인지 본다. 관람일 당일(0일 전)부터 시작하는 구간이 있어야 하고, 같은
 * 날에서 시작하는 구간이 둘일 수 없다. 모양이 틀린 목록이 안쪽까지 가면 규정이 만들어지지 않아 500이 되므로 여기서 400으로 막는다.
 * <p>
 * 비었는지는 {@code @NotEmpty}가, 구간 하나하나의 범위는 구간의 제약이 맡는다 — 그런 목록은 여기서 통과시킨다(칸 하나에 오류 하나). 기준보다
 * 불리한 규정인지는 도메인이 본다(409 CANCELLATION_POLICY_STRICTER_THAN_STANDARD).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CancellationFeeTiersShape.Validator.class)
public @interface CancellationFeeTiersShape {

    String MISSING_PERFORMANCE_DAY = "관람일 당일(0일 전)부터 시작하는 구간을 넣어 주세요.";

    String DUPLICATE_START_DAY = "같은 날에서 시작하는 구간이 둘입니다.";

    String message() default MISSING_PERFORMANCE_DAY;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<CancellationFeeTiersShape, List<CancellationFeeTierRequest>> {

        @Override
        public boolean isValid(List<CancellationFeeTierRequest> tiers,
                ConstraintValidatorContext context) {
            if (tiers == null || tiers.isEmpty() || tiers.stream().anyMatch(Validator::isIncomplete)) {
                return true;
            }
            Set<Integer> startDays = new HashSet<>();
            for (CancellationFeeTierRequest tier : tiers) {
                if (!startDays.add(tier.fromDaysBefore())) {
                    return reject(context, DUPLICATE_START_DAY);
                }
            }
            if (!startDays.contains(0)) {
                return reject(context, MISSING_PERFORMANCE_DAY);
            }
            return true;
        }

        private static boolean isIncomplete(CancellationFeeTierRequest tier) {
            return tier == null || tier.fromDaysBefore() == null || tier.deductionRatePercent() == null;
        }

        private static boolean reject(ConstraintValidatorContext context, String message) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(Objects.requireNonNull(message)).addConstraintViolation();
            return false;
        }
    }
}
