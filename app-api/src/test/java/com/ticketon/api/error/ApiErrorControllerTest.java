package com.ticketon.api.error;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.security.SecurityConfiguration;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 서블릿 필터 단계 오류가 오류 응답 경로(/error)로 넘어온 모습을 흉내 낸다 — 서블릿 컨테이너가 붙이는 오류 속성과 ERROR 디스패치.
 * 실제 내장 서버에서 넘어오는 모습은 통합 테스트(LoginSessionIntegrationTest)가 본다. 보안 설정을 함께 올린다.
 */
@WebMvcTest(controllers = ApiErrorController.class)
@Import(SecurityConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("필터 단계 오류 — Spring Boot 기본 본문 대신 ErrorResponse")
class ApiErrorControllerTest {

    private static final String FAILED_REQUEST_URI = "/api/public/events";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            400 | INVALID_INPUT       | 입력한 내용을 다시 확인해 주세요.
            401 | UNAUTHENTICATED     | 로그인이 필요합니다.
            403 | ACCESS_DENIED       | 이 요청을 처리할 권한이 없습니다.
            404 | NOT_FOUND           | 요청한 주소를 찾을 수 없습니다.
            405 | UNSUPPORTED_REQUEST | 지원하지 않는 요청입니다.
            503 | INTERNAL_ERROR      | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.
            """)
    @DisplayName("상태 코드는 지키고, 코드와 문구는 상태에 맞는 공통 코드를 쓴다")
    void keepsStatusWithCommonErrorCode(int statusCode, String code, String message) throws Exception {
        mockMvc.perform(get("/error").with(errorDispatch(statusCode)))
                .andExpect(status().is(statusCode))
                .andExpect(errorBody(code, message));
    }

    @Test
    @DisplayName("필터에서 난 예외는 500이고, 내부 내용은 응답에 없이 로그에만 남는다")
    void filterExceptionIs500AndHidesInternals(CapturedOutput output) throws Exception {
        String internalDetail = "connection reset while reading session row";

        mockMvc.perform(get("/error").with(errorDispatch(500)).requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                        new IllegalStateException(internalDetail)))
                .andExpect(status().isInternalServerError())
                .andExpect(errorBody("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(content().string(not(containsString(internalDetail))));

        assertThat(output).contains("필터 단계에서 처리하지 못한 오류").contains(internalDetail);
    }

    @Test
    @DisplayName("오류 상태가 없으면 처리하지 못한 오류(500)로 본다")
    void missingStatusIs500() throws Exception {
        mockMvc.perform(get("/error").with(errorDispatch(null)))
                .andExpect(status().isInternalServerError())
                .andExpect(errorBody("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("상태 변경 요청(POST)에서 난 오류도 CSRF 검사에 막히지 않고 그 상태로 응답한다")
    void errorFromPostRequestIsNotBlockedByCsrf() throws Exception {
        mockMvc.perform(post("/error").with(errorDispatch(400)))
                .andExpect(status().isBadRequest())
                .andExpect(errorBody("INVALID_INPUT", "입력한 내용을 다시 확인해 주세요."));
    }

    @Test
    @DisplayName("오류 응답 경로를 직접 부르면 다른 접두사 밖 경로처럼 막힌다")
    void directRequestToErrorPathIsBlocked() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    /** 서블릿 컨테이너가 오류를 /error로 넘길 때처럼 ERROR 디스패치와 오류 속성을 붙인다. */
    private static RequestPostProcessor errorDispatch(Integer statusCode) {
        return request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, FAILED_REQUEST_URI);
            if (statusCode != null) {
                request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, statusCode);
            }
            return request;
        };
    }
}
