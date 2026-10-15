package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.OrganizerLogoImage;
import com.ticketon.domain.organizer.OrganizerLogoImageProblem;
import com.ticketon.domain.policy.ProposedPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주최자 로고로 올린 파일 — 도메인 {@link OrganizerLogoImage}와 같은 규칙(빈 파일 → 용량 상한 → 형식 → 정사각형)으로 보고, 처음 걸린 까닭을 칸
 * 옆 문구로 돌려준다(400). 형식은 올린 쪽이 적은 형식(Content-Type)과 파일 첫머리의 실제 시그니처를 함께 본다 — 파일 이름의 확장자는 보지 않는다.
 * <p>
 * 받을 수 없는 파일이 안쪽까지 가면 값 객체가 만들어지지 않아 500이 되므로 여기서 400으로 막는다.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrganizerLogoFile.Validator.class)
public @interface OrganizerLogoFile {

    String message() default "로고로 올릴 수 없는 파일입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<OrganizerLogoFile, MultipartFile> {

        @Override
        public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
            if (file == null) {
                return true;
            }
            return OrganizerLogoImage.problemOf(file.getContentType(), contentOf(file))
                    .map(problem -> reject(context, problem))
                    .orElse(true);
        }

        private static boolean reject(ConstraintValidatorContext context, OrganizerLogoImageProblem problem) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(messageOf(problem)).addConstraintViolation();
            return false;
        }

        static String messageOf(OrganizerLogoImageProblem problem) {
            return switch (problem) {
                case EMPTY -> "로고 파일을 골라 주세요.";
                case TOO_LARGE -> "로고는 " + ProposedPolicy.ORGANIZER_LOGO_MAXIMUM_SIZE + " 이하로 올려 주세요.";
                case UNSUPPORTED_FORMAT -> "로고는 " + acceptedFormatNames() + " 그림만 올릴 수 있습니다.";
                case NOT_SQUARE -> "로고는 정사각형으로 잘라 올려 주세요.";
            };
        }

        private static String acceptedFormatNames() {
            return ProposedPolicy.ORGANIZER_LOGO_ACCEPTED_FORMATS.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(" 또는 "));
        }

        private static byte[] contentOf(MultipartFile file) {
            try {
                return file.getBytes();
            } catch (IOException exception) {
                throw new UncheckedIOException("올린 파일을 읽지 못했습니다", exception);
            }
        }
    }
}
