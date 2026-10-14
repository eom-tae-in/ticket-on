package com.ticketon.api.security;

import com.ticketon.api.error.ApiErrorCode;
import com.ticketon.api.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 필터가 막은 요청에 컨트롤러 오류와 같은 {@link ErrorResponse}로 응답한다.
 * <p>
 * 컨트롤러에 닿기 전에 막힌 요청은 ApiExceptionHandler를 거치지 않으므로 여기서 같은 모양을 만든다.
 * <ul>
 *   <li>401 UNAUTHENTICATED — 로그인하지 않은 채 로그인이 필요한 경로에 왔다. 로그인이 끊긴(만료·정지·탈퇴) 세션도 여기로 온다.</li>
 *   <li>403 ACCESS_DENIED — 로그인은 했지만 그 영역의 계정 종류가 아니다. CSRF 토큰이 없거나 틀린 요청도 여기로 온다.</li>
 * </ul>
 * 정상적인 거절이라 debug로만 남긴다.
 */
public class SecurityErrorResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorResponseHandler.class);

    private final JsonMapper jsonMapper;

    public SecurityErrorResponseHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.debug("미인증 요청 거절 {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        write(response, HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        log.debug("권한 없는 요청 거절 {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        write(response, HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletResponse response, HttpStatus status, ApiErrorCode errorCode) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(errorCode));
    }
}
