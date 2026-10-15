package com.ticketon.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 주최 관리 컨트롤러 인자로 "누가 어느 주최자로서 일하는지"({@code ActingStaff} — 로그인한 운영진 + 경로의 주최자 id)를 받는다. 컨트롤러는
 * 이 값을 Command·Query에 담아 유스케이스에 넘긴다(.claude/rules/inbound-adapters.md "로그인 사용자 같은 값은 여기서 Command에 담는다").
 * <pre>{@code
 * @PostMapping("/api/organizer/organizers/{organizerId}/events/{eventId}/invitations")
 * IssueInvitationsResponse issue(@CurrentStaff ActingStaff actingStaff, @PathVariable @Positive long eventId,
 *         @Valid @RequestBody IssueInvitationsRequest request) {
 *     return IssueInvitationsResponse.from(issueInvitationsUseCase.issue(request.toCommand(actingStaff, eventId)));
 * }
 * }</pre>
 * <ul>
 *   <li><b>경로</b> — 주최자 하나의 일은 {@value CurrentStaffArgumentResolver#ORGANIZER_PATH} 아래에 둔다. 한 사람이 여러 주최자에
 *       소속될 수 있어, 화면이 주최자 전환으로 고른 주최자의 id를 경로에 담는다(docs/01 "운영자 초대", docs/13 "3. 주최 관리").</li>
 *   <li><b>주최자 id</b> — 경로의 {@code {organizerId}}를 읽는다. 1 이상의 정수가 아니면 400(INVALID_INPUT, 칸 organizerId)이다.
 *       경로에 {@code {organizerId}}가 없는 주소에 붙이면 개발 중 실수이므로 예외를 던진다.</li>
 *   <li><b>로그인</b> — 로그인하지 않았으면 401, 운영진(STAFF)이 아니면 403이다. 주최 관리 영역(/api/organizer)은 보안 필터가 먼저
 *       막는다.</li>
 *   <li><b>권한이 아니다.</b> 이 값은 "그 주최자에서 일하겠다"는 요청일 뿐이다. 소속 · 역할 · 주최자 상태는 유스케이스가 운영진 권한 확인
 *       (CheckStaffPermissionUseCase)으로 본다 — 경로의 주최자 id는 요청한 사람이 바꿀 수 있기 때문이다. 경로에 주최자 안의 자원 id
 *       (행사 등)가 함께 오면 그 자원이 이 주최자의 것인지도 유스케이스가 본다.</li>
 * </ul>
 * API 명세에는 이 인자 대신 경로 파라미터 {@code organizerId}(정수, 1 이상)를 싣는다({@link CurrentAccountConfiguration}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentStaff {
}
