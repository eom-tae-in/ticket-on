package com.ticketon.api.error;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 컨트롤러에서 나온 예외를 {@link ErrorResponse}와 정해진 상태 코드로 바꾼다. 상태 코드 규칙은 package-info에 있다.
 * <p>
 * 처리 메서드마다 {@link ResponseStatus}를 붙여 둔다. springdoc이 이것을 읽어 모든 API의 공통 오류 응답으로 명세에 싣는다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({RuleViolationException.class, ConflictException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleRuleViolation(DomainException exception) {
        return rejected(exception);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException exception) {
        return rejected(exception);
    }

    @ExceptionHandler(PermissionDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handlePermissionDenied(PermissionDeniedException exception) {
        return rejected(exception);
    }

    /** JSON이 깨졌거나 칸 타입이 맞지 않아 본문을 읽지 못했다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("본문을 읽지 못함: {}", exception.getMessage());
        return ErrorResponse.of(ApiErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthenticated(UnauthenticatedException exception) {
        return ErrorResponse.of(ApiErrorCode.UNAUTHENTICATED);
    }

    /**
     * 위에서 다루지 않은 예외. Spring MVC가 상태를 정해 둔 요청 오류(없는 주소, 지원하지 않는 메서드 등)는 그 상태를 지키고,
     * 나머지는 500으로 응답한다. 500은 내부 내용을 숨기고 로그에만 남긴다.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleOthers(Exception exception) {
        if (exception instanceof org.springframework.web.ErrorResponse frameworkError
                && frameworkError.getStatusCode().is4xxClientError()) {
            return frameworkClientError(frameworkError);
        }
        log.error("처리하지 못한 예외", exception);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ApiErrorCode.INTERNAL_ERROR));
    }

    private ErrorResponse rejected(DomainException exception) {
        log.debug("요청 거절: {}", exception.getMessage());
        return ErrorResponse.of(exception.errorCode());
    }

    private ResponseEntity<ErrorResponse> frameworkClientError(org.springframework.web.ErrorResponse frameworkError) {
        HttpStatusCode status = frameworkError.getStatusCode();
        log.debug("요청 오류 {}: {}", status.value(), frameworkError.getBody().getDetail());
        return ResponseEntity.status(status)
                .headers(frameworkError.getHeaders())
                .body(ErrorResponse.of(clientErrorCode(status)));
    }

    private static ApiErrorCode clientErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ApiErrorCode.INVALID_INPUT;
            case 401 -> ApiErrorCode.UNAUTHENTICATED;
            case 403 -> ApiErrorCode.ACCESS_DENIED;
            case 404 -> ApiErrorCode.NOT_FOUND;
            default -> ApiErrorCode.UNSUPPORTED_REQUEST;
        };
    }
}
