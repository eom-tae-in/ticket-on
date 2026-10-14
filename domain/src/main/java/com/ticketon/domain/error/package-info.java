/**
 * 도메인 공통 예외와 오류 코드.
 * <p>
 * 규칙을 어긴 요청은 아래 네 예외 중 하나에 {@link com.ticketon.domain.error.ErrorCode 오류 코드}를 담아 던진다.
 * 어떤 HTTP 상태로 응답할지는 들어오는 어댑터(app-api)가 예외 종류로 정한다. 도메인은 HTTP를 모른다.
 * <ul>
 *   <li>{@link com.ticketon.domain.error.RuleViolationException 규칙 위반} — 지금 상태에서 받아들일 수 없는 요청</li>
 *   <li>{@link com.ticketon.domain.error.NotFoundException 찾을 수 없음} — 가리킨 대상이 없음</li>
 *   <li>{@link com.ticketon.domain.error.PermissionDeniedException 권한 없음} — 그 일을 할 권한이 없음</li>
 *   <li>{@link com.ticketon.domain.error.ConflictException 충돌} — 동시에 들어온 다른 요청에 짐</li>
 * </ul>
 *
 * <h2>오류 코드</h2>
 * 도메인 패키지마다 {@link com.ticketon.domain.error.ErrorCode}를 구현하는 enum을 둔다(예: booking의 {@code BookingErrorCode}).
 * enum 상수 이름이 화면이 분기에 쓰는 코드가 되고, 문구는 화면이 그대로 띄운다.
 * <ul>
 *   <li>코드는 서비스 전체에서 겹치지 않게 뜻을 담아 짓는다({@code NOT_FOUND} 대신 {@code EVENT_NOT_FOUND}).
 *       화면이 코드로 분기하므로 한 번 내보낸 코드는 바꾸지 않는다.</li>
 *   <li>문서가 정해 둔 문구는 글자 그대로 옮긴다(예: "방금 다른 분이 선택했습니다", docs/06).</li>
 *   <li>문구에 내부 사정(번호·표 이름·다른 행사의 존재)을 담지 않는다. 로그용 설명은 예외의 detail에 적는다.
 *       예: 잘못된 쿠폰은 "존재하지 않는 코드"와 구분하지 않는다(docs/05 "잘못된 코드").</li>
 * </ul>
 *
 * <h2>층</h2>
 * 이 패키지는 policy처럼 도메인 패키지의 층(⓪~⑤, ArchitectureTest) 밖에 있다. 모든 도메인 패키지와 application,
 * 바깥 어댑터가 쓸 수 있고, 이 패키지는 다른 도메인 패키지를 부르지 않는다.
 */
package com.ticketon.domain.error;
