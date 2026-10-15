package com.ticketon.api.error;

import com.ticketon.api.error.ErrorResponse.FieldError;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    /** {@code @Valid} 본문·{@code @ModelAttribute} 검증 실패. MethodArgumentNotValidException도 여기로 온다. */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidInput(BindException exception) {
        return invalidInput(ValidationErrors.of(exception.getBindingResult()));
    }

    /** 파라미터 제약({@code @RequestParam @Min(1)} 등) 검증 실패. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidParameters(HandlerMethodValidationException exception) {
        return invalidInput(ValidationErrors.of(exception));
    }

    /**
     * 파라미터 형식이 틀렸다(숫자 칸에 글자 등). 변환기가 아예 없는 경우(ConversionNotSupportedException)는 서버 설정 문제라
     * 여기서 받지 않고 500으로 둔다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidInput(ValidationErrors.invalidFormat(exception.getName()));
    }

    /** 꼭 있어야 할 파라미터가 빠졌다. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingParameter(MissingServletRequestParameterException exception) {
        return invalidInput(ValidationErrors.missing(exception.getParameterName()));
    }

    /** 꼭 있어야 할 파일 칸(multipart의 한 부분)이 빠졌다. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMissingPart(MissingServletRequestPartException exception) {
        return invalidInput(ValidationErrors.missing(exception.getRequestPartName()));
    }

    /**
     * 파일 올리기(multipart) 본문을 읽지 못했다 — 요청 크기의 바깥 울타리(정책 숫자 모음, MultipartConfiguration)를 넘었거나 본문이 깨졌다.
     * 크기를 넘은 것도 413 대신 400으로 답한다 — 파일마다의 상한을 넘은 파일(400)과 같은 입력 오류로 다룬다. 요청을 끝까지 읽기 전에 끊으므로
     * 어느 칸인지 특정하지 않고 fieldErrors는 비운다.
     */
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableMultipart(MultipartException exception) {
        log.debug("파일 올리기 본문을 읽지 못함: {}", exception.getMessage());
        return ErrorResponse.of(ApiErrorCode.INVALID_INPUT);
    }

    /** JSON이 깨졌거나 칸 타입이 맞지 않아 본문을 읽지 못했다. 어느 칸인지 특정하지 않으므로 fieldErrors는 비운다. */
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
     * Spring Security 예외(메서드 보안의 권한 없음 등)는 여기서 응답하지 않고 보안 필터로 되돌린다. 필터가 로그인 여부를 보고
     * 401(로그인하지 않음)과 403(권한 없음)을 나눠 같은 ErrorResponse로 응답한다(api/security). 아래 마지막 처리(500)에
     * 걸리지 않게 먼저 받는다. 응답을 만들지 않으므로 명세에는 싣지 않는다.
     */
    @Hidden
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowToSecurityFilter(RuntimeException exception) {
        throw exception;
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

    private ErrorResponse invalidInput(List<FieldError> fieldErrors) {
        log.debug("입력 검증 실패: {}", fieldErrors);
        return ErrorResponse.of(ApiErrorCode.INVALID_INPUT, fieldErrors);
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
                .body(ErrorResponse.of(ApiErrorCode.forStatus(status)));
    }
}
