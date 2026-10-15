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
 *   <tr><td>입력 검증 실패(Bean Validation), 형식이 틀린 파라미터, 빠진 파라미터</td><td>400</td>
 *       <td>INVALID_INPUT + fieldErrors(칸 이름·문구)</td></tr>
 *   <tr><td>읽을 수 없는 본문(깨진 JSON, 칸 타입이 맞지 않는 본문)</td><td>400</td>
 *       <td>INVALID_INPUT, fieldErrors는 빈 배열 — 칸을 특정하지 않는다</td></tr>
 *   <tr><td>빠진 파일 칸(multipart의 한 부분)</td><td>400</td><td>INVALID_INPUT + 그 칸 이름</td></tr>
 *   <tr><td>읽을 수 없는 파일 올리기 본문(요청 크기의 바깥 울타리를 넘음 — 413이 아니다, 깨진 multipart)</td><td>400</td>
 *       <td>INVALID_INPUT, fieldErrors는 빈 배열 — 끝까지 읽기 전에 끊으므로 칸을 특정하지 않는다</td></tr>
 *   <tr><td>{@link com.ticketon.api.error.UnauthenticatedException 미인증}</td><td>401</td><td>UNAUTHENTICATED</td></tr>
 *   <tr><td>{@link com.ticketon.domain.error.PermissionDeniedException 권한 없음}</td><td>403</td><td>도메인 오류 코드</td></tr>
 *   <tr><td>{@link com.ticketon.domain.error.NotFoundException 찾을 수 없음}</td><td>404</td><td>도메인 오류 코드</td></tr>
 *   <tr><td>{@link com.ticketon.domain.error.RuleViolationException 규칙 위반},
 *           {@link com.ticketon.domain.error.ConflictException 충돌(동시성 패배)}</td><td>409</td><td>도메인 오류 코드</td></tr>
 *   <tr><td>데이터베이스가 잠금 경합으로 끊은 요청(교착 40P01 · 직렬화 실패 40001 · 잠금 대기 실패 55P03 —
 *           {@link org.springframework.dao.PessimisticLockingFailureException})</td><td>409</td>
 *       <td>CONCURRENT_REQUEST — 반영된 것이 없어 다시 보내면 된다. 서버가 다시 시도하지는 않는다</td></tr>
 *   <tr><td>Spring MVC가 상태를 정한 그 밖의 요청 오류(없는 주소 404, 지원하지 않는 메서드 405 등)</td><td>그 상태</td>
 *       <td>NOT_FOUND · UNSUPPORTED_REQUEST 등 {@link com.ticketon.api.error.ApiErrorCode}, fieldErrors는 빈 배열</td></tr>
 *   <tr><td>Spring Security 예외(메서드 보안의 권한 없음 등)</td><td>401 / 403</td>
 *       <td>보안 필터로 되돌려 로그인하지 않았으면 UNAUTHENTICATED, 했으면 ACCESS_DENIED</td></tr>
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
 * <h2>입력 검증 문구</h2>
 * Bean Validation에 걸린 칸은 fieldErrors에 칸 이름과 제약의 문구로 담는다({@link com.ticketon.api.error.ValidationErrors}).
 * Request의 제약에는 화면에 띄울 문구를 직접 적는다. 다국어는 만들지 않으므로(docs/99 "만들지 않기로 한 것")
 * 문구의 언어는 요청 언어(Accept-Language)와 상관없이 한국어로 고정한다(application.yml {@code spring.web.locale}).
 *
 * <h2>OpenAPI 명세</h2>
 * 처리 메서드마다 붙인 {@code @ResponseStatus}를 springdoc이 읽어 모든 API에 400·401·403·404·409·500 → ErrorResponse를 싣는다.
 * ErrorResponse 스키마는 API가 하나도 없어도 실린다({@link com.ticketon.api.error.ErrorResponseOpenApiConfiguration}).
 *
 * <h2>로그</h2>
 * 400·401·403·404·409는 정상적인 거절이라 debug로만 남긴다. 다만 잠금 경합(409 CONCURRENT_REQUEST)은 잠금 순서를 어긴 흐름을 찾을 수 있게
 * 예외 전체를 warn으로 남긴다. 500은 예외 전체를 error로 남기고, 응답에는 정해진 문구만 보낸다.
 *
 * <h2>컨트롤러 밖에서 막히거나 난 오류</h2>
 * 같은 {@link com.ticketon.api.error.ErrorResponse} 모양으로 응답한다. 화면은 어디서 막혔는지 몰라도 된다.
 * <ul>
 *   <li>보안 필터가 막은 요청(미인증 401 UNAUTHENTICATED, 권한 없음·CSRF 실패 403 ACCESS_DENIED)은 이 처리기를 거치지 않고
 *       보안 설정이 응답한다(api/security의 SecurityErrorResponseHandler).</li>
 *   <li>메서드 보안이 던지는 Spring Security 예외는 위 표처럼 보안 필터로 되돌려 같은 401/403이 된다. 500으로 새지 않는다.</li>
 *   <li>그 밖에 서블릿 필터 단계에서 난 오류(예외, 방화벽이 거절한 주소 등)는 Spring Boot 기본 오류 본문 대신
 *       {@link com.ticketon.api.error.ApiErrorController}가 상태를 지켜 응답한다.</li>
 * </ul>
 */
package com.ticketon.api.error;
