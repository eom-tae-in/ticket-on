package com.ticketon.api.error;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 시험 전용 컨트롤러 — 주소마다 예외 하나를 던진다.
 * <p>
 * {@link TestComponent}라 컴포넌트 스캔에 잡히지 않는다. 쓰는 테스트가 {@code @Import}로 넣는다.
 */
@TestComponent
@RestController
@RequestMapping("/test/errors")
class ErrorTestController {

    static final String INTERNAL_DETAIL = "lock timeout on table ticket_internal";

    static final String DEADLOCK_DETAIL = "ERROR: deadlock detected — Process 4242 waits for ShareLock on transaction 777";

    @GetMapping("/rule-violation")
    void ruleViolation() {
        throw new RuleViolationException(TestErrorCode.COUPON_NOT_APPLICABLE, "couponCode=SPRING10");
    }

    @GetMapping("/duplicate-phone")
    void duplicatePhone() {
        throw new RuleViolationException(TestErrorCode.PHONE_ALREADY_REGISTERED);
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException(TestErrorCode.SEAT_ALREADY_TAKEN, "seatId=12");
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException(TestErrorCode.TICKET_NOT_FOUND);
    }

    @GetMapping("/permission-denied")
    void permissionDenied() {
        throw new PermissionDeniedException(TestErrorCode.OWNER_ONLY);
    }

    @GetMapping("/unauthenticated")
    void unauthenticated() {
        throw new UnauthenticatedException();
    }

    /** 교착(40P01) — 실제 서버에서는 jOOQ가 PostgreSQL 오류를 이 계열의 Spring 예외로 바꿔 던진다. */
    @GetMapping("/deadlock")
    void deadlock() {
        throw new PessimisticLockingFailureException(DEADLOCK_DETAIL);
    }

    /** 잠금 대기 실패(55P03) — NOWAIT · 잠금 대기 시간 초과. */
    @GetMapping("/lock-not-available")
    void lockNotAvailable() {
        throw new CannotAcquireLockException(DEADLOCK_DETAIL);
    }

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException(INTERNAL_DETAIL);
    }

    /** 변환기가 없는 타입 — 요청이 아니라 서버 설정 문제다. */
    @GetMapping("/unconvertible")
    void unconvertible(@RequestParam Unconvertible value) {
    }

    @GetMapping("/param")
    void param(@RequestParam @Min(1) int quantity) {
    }

    @PostMapping("/body")
    void body(@Valid @RequestBody SampleRequest request) {
    }

    @GetMapping("/search")
    void search(@Valid @ModelAttribute SampleQuery query) {
    }

    @PostMapping("/range")
    void range(@Valid @RequestBody SampleRange range) {
    }

    /** 파라미터 제약이 있어 메서드 검증이 걸린다 — {@code @Valid} 본문도 메서드 검증에서 함께 검사된다. */
    @PostMapping("/body-with-param")
    void bodyWithParam(@RequestParam @Min(1) int count, @Valid @RequestBody SampleRequest request) {
    }

    /** 파일 올리기 요청이 바깥 울타리를 넘었다 — 실제 서버에서는 multipart를 읽다가 이 예외가 난다. */
    @PostMapping("/upload-too-large")
    void uploadTooLarge() {
        throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
    }

    /** 깨진 multipart 본문 — 실제 서버에서는 multipart를 읽다가 이 예외가 난다. */
    @PostMapping("/broken-multipart")
    void brokenMultipart() {
        throw new MultipartException("Failed to parse multipart servlet request");
    }

    @PostMapping("/file")
    void file(@RequestPart("attachment") MultipartFile attachment) {
    }

    /** 제약 문구를 직접 적은 칸(name, row)과 기본 문구를 쓰는 칸(quantity)을 함께 둔다. */
    record SampleRequest(
            @NotBlank(message = "이름을 입력해 주세요") String name,
            @Min(1) int quantity,
            @Valid List<SampleSeat> seats) {
    }

    record SampleSeat(@NotBlank(message = "열을 입력해 주세요") String row) {
    }

    record SampleQuery(@Min(1) Integer page) {
    }

    /** 두 칸을 함께 보는 검증 — 특정 칸에 걸리지 않는다. */
    @ValidRange
    record SampleRange(int min, int max) {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RangeValidator.class)
    @interface ValidRange {

        String message() default "최소가 최대보다 클 수 없습니다";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static final class RangeValidator implements ConstraintValidator<ValidRange, SampleRange> {

        @Override
        public boolean isValid(SampleRange range, ConstraintValidatorContext context) {
            return range == null || range.min() <= range.max();
        }
    }

    static final class Unconvertible {
    }
}
