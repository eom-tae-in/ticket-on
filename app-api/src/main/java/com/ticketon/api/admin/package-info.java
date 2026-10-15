/**
 * admin — 컨트롤러와 Request/Response. Request → Command, Result → Response로 바꿔 유스케이스를 부른다.
 * <p>
 * 관리자 영역은 {@code /api/admin/**}(관리자·최고 관리자)이고, 최고 관리자만 하는 일은 {@code /api/admin/super/**} 아래에 둔다
 * (api/security). 관리자 로그인·로그아웃은 로그인 없이 여는 {@code /api/auth/admin}에 있다(api/account).
 */
package com.ticketon.api.admin;
