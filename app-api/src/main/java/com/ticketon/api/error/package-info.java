/**
 * 오류 응답 형식과 공통 예외 처리 — 모든 오류는 {@link com.ticketon.api.error.ErrorResponse}(code, message, fieldErrors)로 응답한다.
 * <p>
 * 화면 타입은 Response에서 OpenAPI로 생성되므로(.claude/rules/inbound-adapters.md) 오류 모양을 하나로 둔다.
 * 화면은 code로 분기하고 message를 그대로 띄운다.
 *
 * <h2>상태 코드</h2>
 * <table>
 *   <caption>예외 → 상태 코드 → code</caption>
 *   <tr><th>예외</th><th>상태</th><th>code</th></tr>
 *   <tr><td>입력 오류(읽을 수 없는 본문, 빠진 파라미터 등)</td><td>400</td><td>INVALID_INPUT</td></tr>
 *   <tr><td>{@link com.ticketon.api.error.UnauthenticatedException 미인증}</td><td>401</td><td>UNAUTHENTICATED</td></tr>
 *   <tr><td>{@link com.ticketon.domain.error.PermissionDeniedException 권한 없음}</td><td>403</td><td>도메인 오류 코드</td></tr>
 *   <tr><td>{@link com.ticketon.domain.error.NotFoundException 찾을 수 없음}</td><td>404</td><td>도메인 오류 코드</td></tr>
 *   <tr><td>{@link com.ticketon.domain.error.RuleViolationException 규칙 위반},
 *           {@link com.ticketon.domain.error.ConflictException 충돌(동시성 패배)}</td><td>409</td><td>도메인 오류 코드</td></tr>
 *   <tr><td>Spring MVC가 상태를 정한 요청 오류(없는 주소 404, 지원하지 않는 메서드 405 등)</td><td>그 상태</td>
 *       <td>NOT_FOUND · UNSUPPORTED_REQUEST 등 {@link com.ticketon.api.error.ApiErrorCode}</td></tr>
 *   <tr><td>그 밖의 모든 예외</td><td>500</td><td>INTERNAL_ERROR — 내부 내용은 숨기고 로그에만 남긴다</td></tr>
 * </table>
 *
 * <h2>규칙 위반과 충돌은 409 하나로</h2>
 * 둘 다 "요청 형식은 맞지만 지금 상태와 맞지 않아 받아들일 수 없다"는 뜻이라 같은 상태로 묶는다. 화면은 상태가 아니라 code로 나눈다.
 * 422(내용 자체가 틀림)가 아니라 409(현재 상태와 충돌)를 고른 이유:
 * <ul>
 *   <li>동시성 패배("방금 다른 분이 선택했습니다", docs/06)는 요청 내용이 아니라 그 사이 바뀐 상태 때문에 진다 — 409의 뜻 그대로다.</li>
 *   <li>규칙 위반도 대부분 상태에 달렸다: 이미 가입된 번호(docs/01), 매진·판매 종료, 기한이 지난 취소.
 *       같은 요청이 어제는 됐고 오늘은 안 된다.</li>
 *   <li>잘못된 쿠폰 코드(docs/05)도 "존재하지 않는 코드"와 구분하지 않으므로 형식 오류(400)나 없음(404)이 아니라 규칙 위반이다.</li>
 * </ul>
 *
 * <h2>로그</h2>
 * 400·401·403·404·409는 정상적인 거절이라 debug로만 남긴다. 500은 예외 전체를 error로 남기고, 응답에는 정해진 문구만 보낸다.
 *
 * <h2>여기서 다루지 않는 것 (#8 보안 설정)</h2>
 * 컨트롤러에 닿기 전 보안 필터가 막는 요청(미인증 401, 권한 없음 403)은 이 처리기를 거치지 않는다. 보안 설정에서 같은
 * {@link com.ticketon.api.error.ErrorResponse}로 응답하게 한다({@code ApiErrorCode.UNAUTHENTICATED}, {@code ACCESS_DENIED}).
 * 메서드 보안이 던지는 Spring Security 예외는 여기 마지막 처리(500)에 걸리지 않게 그때 따로 다룬다.
 */
package com.ticketon.api.error;
