package com.ticketon.api.error;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/unexpected")
    void unexpected() {
        throw new IllegalStateException(INTERNAL_DETAIL);
    }

    @GetMapping("/param")
    void param(@RequestParam int quantity) {
    }

    @PostMapping("/body")
    void body(@RequestBody SampleRequest request) {
    }

    record SampleRequest(String name, int quantity) {
    }
}
