/**
 * 로그인과 영역별 접근 규칙 — 세션 쿠키 + DB 저장 세션(docs/16 "로그인").
 * <p>
 * JWT로 바꾸지 않는다. 관리자 정지·탈퇴·검표 계정 비밀번호 재발급은 이미 로그인된 세션을 바로 끊어야 하는데(docs/01),
 * 이미 발급한 토큰은 바로 끊기 어렵다. 세션은 Spring Session JDBC가 spring_session 표(V1)에 저장하고, 끊을 때는
 * application의 SessionTerminationPort를 부른다.
 *
 * <h2>로그인 주체</h2>
 * <ul>
 *   <li>로그인 주체는 계정 종류 + id({@link com.ticketon.domain.account.AccountRef})다. 세션에는 주체 이름 "종류:id"로 저장된다
 *       (spring_session.principal_name, 예: {@code AUDIENCE:7}). 같은 이메일로 만든 관객 계정과 운영진 로그인이 섞이지 않는다.</li>
 *   <li>로그인 컨트롤러는 로그인 유스케이스로 계정을 확인한 뒤 {@link com.ticketon.api.security.SessionLogin}으로 세션을 연다.
 *       로그인 뒤 돌아갈 화면 경로는 같은 사이트 안의 경로만 돌려준다({@link com.ticketon.api.security.ReturnPath}).</li>
 *   <li>로그아웃은 같은 컨트롤러가 {@link com.ticketon.api.security.SessionLogin#logOut}으로 지금 세션 하나만 끊는다.
 *       같은 주체가 다른 기기에서 로그인한 세션은 남는다. 정지·탈퇴처럼 모두 끊을 때는 SessionTerminationPort를 쓴다.</li>
 *   <li>컨트롤러는 {@link com.ticketon.api.security.CurrentAccount @CurrentAccount}로 주체를 받아 Command에 담는다.</li>
 *   <li>관객 카카오·구글 로그인의 시작·콜백 두 주소({@code /api/auth/audience/oauth2/authorization/*}, {@code …/callback/*})는 이 기본
 *       필터 묶음보다 먼저 걸리는 별도 묶음이 맡는다(api/account AudienceOAuthSecurityConfiguration). 제공자 인증 정보는 세션 주체로
 *       저장하지 않고, 로그인할 관객 계정이 정해지면 같은 {@link com.ticketon.api.security.SessionLogin}으로 세션을 연다.</li>
 * </ul>
 *
 * <h2>경로 접두사 — 영역마다 하나, 계정 종류에 묶는다</h2>
 * 계정 4종은 서로의 영역에 들어가지 못한다(docs/01 "계정 4종"). 화면 영역(docs/13, web/src/app)과 같게 나눈다.
 * <table>
 *   <caption>경로 → 들어갈 수 있는 계정</caption>
 *   <tr><th>경로</th><th>들어갈 수 있는 계정</th><th>무엇을 두나</th></tr>
 *   <tr><td>{@code /api/public/**}</td><td>누구나(로그인 없이)</td>
 *       <td>공개 영역(docs/13 "1. 공개 영역"): 행사 목록·검색·상세, 주최자 페이지, 운영진 프로필,
 *           링크로만 여는 페이지(공유받은 티켓, 초대권 단체·개별 링크, 마케팅 수신 거부, 비공개 행사 링크)</td></tr>
 *   <tr><td>{@code /api/auth/**}</td><td>누구나(로그인 없이)</td>
 *       <td>로그인·로그아웃·가입·이메일 인증·비밀번호 찾기 — 네 계정 종류 모두. 관객 카카오·구글 로그인 결과·추가 정보 입력 가입</td></tr>
 *   <tr><td>{@code /api/audience/**}</td><td>관객(AUDIENCE)</td>
 *       <td>예매, 마이페이지, 알림함, 알림 받기, 신고. 주최자 쪽 로그인으로는 예매할 수 없다(docs/01 "같은 이메일로 양쪽 가입").</td></tr>
 *   <tr><td>{@code /api/organizer/**}</td><td>운영진(STAFF)</td>
 *       <td>주최 관리. 소유자·운영자 권한은 주최자마다 다르므로 여기서 나누지 않고 유스케이스가 확인한다(docs/01 "권한 두 가지").</td></tr>
 *   <tr><td>{@code /api/admin/super/**}</td><td>최고 관리자(ADMIN 중 등급 SUPER_ADMIN)</td>
 *       <td>최고 관리자만 하는 일 — 관리자 계정 발급, 영구 정지(docs/01 "관리자 2등급", docs/13 "※ 최고 관리자만").
 *           관리자 로그인이 세션에 등급을 담는다({@link com.ticketon.api.security.SessionLogin#logInAdmin}).
 *           관리자(ADMIN 등급)로 오면 403이다. 규칙의 마지막 판정은 유스케이스가 저장소의 등급으로 한 번 더 한다.</td></tr>
 *   <tr><td>{@code /api/admin/**}</td><td>관리자(ADMIN) — 두 등급 모두</td>
 *       <td>플랫폼 관리. 최고 관리자만 하는 일은 위 {@code /api/admin/super/**} 아래에 둔다.</td></tr>
 *   <tr><td>{@code /api/checkin/**}</td><td>검표(CHECKIN), 운영진(STAFF)</td>
 *       <td>검표 화면. 운영진도 같은 화면을 연다(docs/13 "검표 화면 열기 — 운영진도 같은 화면").
 *           검표 계정은 이 영역만 들어간다(docs/01 "검표 계정"). 운영진만 되는 동작(티켓 없이 입장 처리)을 막는 방법은
 *           그 기능 이슈에서 정한다.</td></tr>
 *   <tr><td>{@code /v3/api-docs/**}, {@code /swagger-ui/**}</td><td>누구나</td><td>API 명세 — 화면 타입 생성(npm run api:types)</td></tr>
 *   <tr><td>{@code /actuator/health/**}</td><td>누구나</td><td>서버 상태 확인</td></tr>
 *   <tr><td>그 밖의 모든 경로</td><td>아무도</td>
 *       <td>새 API는 반드시 위 접두사 중 하나 아래에 둔다. 빠뜨리면 막힌다(로그인 전 401, 로그인 후 403).</td></tr>
 * </table>
 * 세션을 쓰지 않는 바깥 호출(토스 웹훅 등)은 그 기능을 만드는 이슈에서 경로와 CSRF 예외를 함께 정한다.
 *
 * <h2>응답</h2>
 * 보안 필터가 막은 요청도 {@link com.ticketon.api.error.ErrorResponse}로 응답한다
 * ({@link com.ticketon.api.security.SecurityErrorResponseHandler}).
 * <ul>
 *   <li>401 UNAUTHENTICATED — 로그인이 필요한 경로에 로그인 없이 왔다. 세션이 만료됐거나 끊긴 경우도 같다.</li>
 *   <li>403 ACCESS_DENIED — 그 영역의 계정 종류가 아니다. CSRF 토큰이 없거나 틀려도 403이다.</li>
 * </ul>
 * 컨트롤러 안에서 Spring Security 예외(메서드 보안 등)가 나도 ApiExceptionHandler가 보안 필터로 되돌려 같은 401/403이 된다.
 *
 * <h2>세션 쿠키와 CSRF</h2>
 * <ul>
 *   <li>세션 쿠키는 HttpOnly, SameSite=Lax이고 운영 프로필(prod)에서 Secure다(application.yml, application-prod.yml).</li>
 *   <li>비활성 만료 시간은 정책 숫자 모음의 '확인 필요' 구역 값이다({@link com.ticketon.api.security.LoginSessionConfiguration}).</li>
 *   <li>쿠키로 로그인하므로 Spring Security 기본 CSRF 보호를 끄지 않는다. 토큰은 기본대로 세션에 둔다. 화면이 토큰을 받아 보내는
 *       방법은 화면과 서버의 연결 방식(같은 출처 프록시 / 별도 도메인)을 정할 때(#136) 함께 정한다.</li>
 *   <li>CORS는 열지 않는다(같은 출처만). 별도 도메인으로 정해지면 그때 허용 출처를 연다(#136).</li>
 * </ul>
 */
package com.ticketon.api.security;
