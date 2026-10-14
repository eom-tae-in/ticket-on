package com.ticketon.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자로 지금 로그인한 주체를 받는다. 컨트롤러는 이 값을 Command에 담아 유스케이스에 넘긴다
 * (.claude/rules/inbound-adapters.md "로그인 사용자 같은 값은 여기서 Command에 담는다").
 * <pre>{@code
 * @PostMapping("/api/audience/bookings")
 * BookingResponse book(@CurrentAccount AccountRef account, @Valid @RequestBody BookingRequest request) {
 *     return BookingResponse.from(bookingUseCase.book(request.toCommand(account)));
 * }
 * }</pre>
 * <ul>
 *   <li>{@code AccountRef} — 로그인이 필요하다. 로그인하지 않았으면 401(UNAUTHENTICATED)로 응답한다.
 *       영역 경로(/api/audience 등)는 보안 필터가 먼저 막으므로 이 인자까지 오면 늘 로그인해 있다.</li>
 *   <li>{@code Optional<AccountRef>} — 로그인 없이도 열리는 공개 경로에서, 로그인했으면 주체를 받는다.</li>
 * </ul>
 * 세션에서 오는 값이라 API 명세의 요청 파라미터로 싣지 않는다({@link CurrentAccountConfiguration}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentAccount {
}
