package com.ticketon.api.error;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러 밖(서블릿 필터 단계)에서 난 오류도 {@link ErrorResponse}로 응답한다. Spring Boot 기본 오류 본문을 대신한다.
 * <p>
 * 필터에서 예외가 나거나 필터가 오류 상태를 보내면(예: 보안 방화벽이 거절한 주소 400) 서블릿 컨테이너가 이 경로로 넘긴다.
 * 상태 코드는 그대로 지키고, 코드와 문구는 상태에 맞는 공통 코드({@link ApiErrorCode#forStatus})를 쓴다. 500대는 원래 예외를
 * 로그에만 남기고 응답에는 정해진 문구만 보낸다. 보안 필터는 이 오류 전달(ERROR 디스패치)을 막지 않는다(api/security).
 */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping("${spring.web.error.path:${error.path:/error}}")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        HttpStatusCode status = statusOf(request);
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (status.is5xxServerError()) {
            log.error("필터 단계에서 처리하지 못한 오류 {} {}", status.value(), requestUri, exceptionOf(request));
        } else {
            log.debug("필터 단계 요청 오류 {} {}", status.value(), requestUri);
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(ApiErrorCode.forStatus(status)));
    }

    /** 오류 상태가 없거나 오류 상태가 아니면 처리하지 못한 오류로 본다. */
    private static HttpStatusCode statusOf(HttpServletRequest request) {
        if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer statusCode && statusCode >= 400) {
            return HttpStatusCode.valueOf(statusCode);
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static Throwable exceptionOf(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable exception ? exception : null;
    }
}
