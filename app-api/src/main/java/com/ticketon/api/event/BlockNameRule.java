package com.ticketon.api.event;

import com.ticketon.domain.event.BlockName;
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
 * 블록 이름 길이 규칙 — 도메인 {@link BlockName}과 같은 규칙(앞뒤 공백을 걷고 NFC로 맞춘 뒤 {@value ProposedPolicy#BLOCK_NAME_MAXIMUM_LENGTH}자
 * 이하, 확인 필요)으로 본다({@link BlockName#isAcceptable}). 글자 수는 사람이 세는 대로 센다.
 * <p>
 * 비웠거나 공백뿐이면 통과한다 — 블록 이름은 비우면 "블록 N"을 붙이는 칸이다(docs/02 "블록"). 너무 긴 이름이 안쪽까지 가면 값 객체가 만들어지지
 * 않아 500이 되므로 여기서 400으로 막는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BlockNameRule.Validator.class)
public @interface BlockNameRule {

    String message() default "블록 이름은 " + ProposedPolicy.BLOCK_NAME_MAXIMUM_LENGTH + "자 이하로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<BlockNameRule, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return BlockName.isAcceptable(value);
        }
    }
}
