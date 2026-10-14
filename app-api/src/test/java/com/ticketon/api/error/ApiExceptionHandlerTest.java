package com.ticketon.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

@WebMvcTest(controllers = ErrorTestController.class)
@Import(ErrorTestController.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("오류 응답 — 예외마다 정해진 상태 코드와 ErrorResponse 본문")
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("규칙 위반은 409와 도메인 오류 코드·문구다 (docs/05 \"잘못된 코드\")")
    void ruleViolationIsConflict() throws Exception {
        mockMvc.perform(get("/test/errors/rule-violation"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("COUPON_NOT_APPLICABLE", "이 행사에서 사용할 수 없는 코드입니다"));
    }

    @Test
    @DisplayName("이미 가입된 번호도 규칙 위반이라 409다 (docs/01)")
    void duplicatePhoneIsConflict() throws Exception {
        mockMvc.perform(get("/test/errors/duplicate-phone"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("PHONE_ALREADY_REGISTERED", "이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요"));
    }

    @Test
    @DisplayName("동시성 패배는 규칙 위반과 같은 409다 (docs/06 \"두 사람이 동시에 같은 자리를 눌렀다면\")")
    void concurrencyLossIsConflict() throws Exception {
        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(errorBody("SEAT_ALREADY_TAKEN", "방금 다른 분이 선택했습니다"));
    }

    @Test
    @DisplayName("로그용 detail은 응답에 나가지 않는다")
    void detailIsNotExposed() throws Exception {
        mockMvc.perform(get("/test/errors/conflict"))
                .andExpect(content().string(not(containsString("seatId"))));
    }

    @Test
    @DisplayName("찾을 수 없음은 404다")
    void notFoundIs404() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("TICKET_NOT_FOUND", "티켓을 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("권한 없음은 403이다 (docs/01 \"권한 두 가지\")")
    void permissionDeniedIs403() throws Exception {
        mockMvc.perform(get("/test/errors/permission-denied"))
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY", "소유자만 할 수 있습니다"));
    }

    @Test
    @DisplayName("미인증은 401이다")
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/test/errors/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    @Test
    @DisplayName("처리하지 못한 예외는 500이고, 내부 내용은 응답에 없이 로그에만 남는다")
    void unexpectedIs500AndHidesInternals(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(errorBody("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(content().string(not(containsString(ErrorTestController.INTERNAL_DETAIL))));

        assertThat(output).contains("처리하지 못한 예외").contains(ErrorTestController.INTERNAL_DETAIL);
    }

    @Test
    @DisplayName("읽을 수 없는 본문은 400이다")
    void unreadableBodyIs400() throws Exception {
        mockMvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(errorBody("INVALID_INPUT", "입력한 내용을 다시 확인해 주세요."));
    }

    @Test
    @DisplayName("빠진 파라미터는 400이다")
    void missingParameterIs400() throws Exception {
        mockMvc.perform(get("/test/errors/param"))
                .andExpect(status().isBadRequest())
                .andExpect(errorBody("INVALID_INPUT", "입력한 내용을 다시 확인해 주세요."));
    }

    @Test
    @DisplayName("없는 주소는 404, 코드는 NOT_FOUND다")
    void unknownPathIs404() throws Exception {
        mockMvc.perform(get("/test/errors/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(errorBody("NOT_FOUND", "요청한 주소를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405와 Allow 헤더를 지키고, 코드는 UNSUPPORTED_REQUEST다")
    void unsupportedMethodKeepsStatus() throws Exception {
        mockMvc.perform(post("/test/errors/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(errorBody("UNSUPPORTED_REQUEST", "지원하지 않는 요청입니다."));
    }

    private static ResultMatcher errorBody(String code, String message) {
        return result -> {
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.message").value(message).match(result);
            jsonPath("$.fieldErrors").isArray().match(result);
            jsonPath("$.fieldErrors").isEmpty().match(result);
        };
    }
}
