package com.ticketon.api.error;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 컨트롤러에서 나온 예외의 응답만 본다. 보안 필터가 막는 요청의 응답은 api/security의 테스트가 본다.
 * 시험용 주소(/test/errors)는 영역 접두사 밖이라 보안 필터가 모두 막으므로 여기서는 필터를 빼고 돌린다.
 */
@WebMvcTest(controllers = ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
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
    @DisplayName("파일 올리기 요청이 바깥 울타리를 넘으면 413이 아니라 400이고, 칸을 특정하지 않는다")
    void tooLargeUploadIs400() throws Exception {
        mockMvc.perform(post("/test/errors/upload-too-large"))
                .andExpect(status().isBadRequest())
                .andExpect(errorBody("INVALID_INPUT", "입력한 내용을 다시 확인해 주세요."));
    }

    @Test
    @DisplayName("깨진 파일 올리기 본문은 500이 아니라 400이다")
    void brokenMultipartIs400() throws Exception {
        mockMvc.perform(post("/test/errors/broken-multipart"))
                .andExpect(status().isBadRequest())
                .andExpect(errorBody("INVALID_INPUT", "입력한 내용을 다시 확인해 주세요."));
    }

    @Test
    @DisplayName("빠진 파일 칸은 400이고, 칸 이름을 담는다")
    void missingFilePartIs400() throws Exception {
        mockMvc.perform(multipart("/test/errors/file").file(new MockMultipartFile("other", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(fieldError("attachment", "필수 항목입니다."));
    }

    @Test
    @DisplayName("빠진 파라미터는 400이고, 파라미터 이름을 칸 이름으로 담는다")
    void missingParameterIs400() throws Exception {
        mockMvc.perform(get("/test/errors/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력한 내용을 다시 확인해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(fieldError("quantity", "필수 항목입니다."));
    }

    @Test
    @DisplayName("본문 검증 실패는 400이고, 걸린 칸마다 칸 이름과 문구를 fieldErrors에 담는다")
    void invalidBodyHasFieldErrors() throws Exception {
        String body = """
                {"name": " ", "quantity": 0, "seats": [{"row": ""}]}
                """;

        mockMvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("입력한 내용을 다시 확인해 주세요."))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(fieldError("name", "이름을 입력해 주세요"))
                .andExpect(fieldError("quantity", "1 이상이어야 합니다"))
                .andExpect(fieldError("seats[0].row", "열을 입력해 주세요"));
    }

    @Test
    @DisplayName("두 칸을 함께 보는 검증 실패는 칸 이름 대신 Request 이름으로 담는다")
    void crossFieldValidationUsesRequestName() throws Exception {
        mockMvc.perform(post("/test/errors/range").contentType(MediaType.APPLICATION_JSON).content("{\"min\": 5, \"max\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(fieldError("sampleRange", "최소가 최대보다 클 수 없습니다"));
    }

    @Test
    @DisplayName("메서드 검증에서는 본문 칸 오류와 파라미터 오류를 함께 담는다")
    void methodValidationCollectsBodyAndParameterErrors() throws Exception {
        String body = """
                {"name": "", "quantity": 1, "seats": []}
                """;

        mockMvc.perform(post("/test/errors/body-with-param")
                        .param("count", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(fieldError("count", "1 이상이어야 합니다"))
                .andExpect(fieldError("name", "이름을 입력해 주세요"));
    }

    @Test
    @DisplayName("검증 문구는 요청 언어와 상관없이 한국어다 (다국어 없음, docs/99)")
    void validationMessageIsKoreanRegardlessOfAcceptLanguage() throws Exception {
        String body = """
                {"name": "한서윤", "quantity": 0, "seats": []}
                """;

        mockMvc.perform(post("/test/errors/body")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(fieldError("quantity", "1 이상이어야 합니다"));
    }

    @Test
    @DisplayName("파라미터 제약 검증 실패는 400이고, 파라미터 이름을 칸 이름으로 담는다")
    void invalidParameterHasFieldErrors() throws Exception {
        mockMvc.perform(get("/test/errors/param").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(fieldError("quantity", "1 이상이어야 합니다"));
    }

    @Test
    @DisplayName("형식이 틀린 파라미터는 400이고, 내부 타입 이름 대신 정해진 문구를 담는다")
    void typeMismatchedParameterHasFieldError() throws Exception {
        mockMvc.perform(get("/test/errors/param").param("quantity", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(fieldError("quantity", "형식이 올바르지 않습니다."))
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    @DisplayName("변환기가 없는 파라미터는 입력 오류가 아니라 서버 문제라 500이다")
    void unconvertibleParameterIs500() throws Exception {
        mockMvc.perform(get("/test/errors/unconvertible").param("value", "x"))
                .andExpect(status().isInternalServerError())
                .andExpect(errorBody("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("조회 조건 객체의 형식 오류와 검증 실패도 fieldErrors로 담는다")
    void invalidQueryObjectHasFieldErrors() throws Exception {
        mockMvc.perform(get("/test/errors/search").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(fieldError("page", "형식이 올바르지 않습니다."))
                .andExpect(content().string(not(containsString("java.lang"))));

        mockMvc.perform(get("/test/errors/search").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(fieldError("page", "1 이상이어야 합니다"));
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

    private static ResultMatcher fieldError(String field, String message) {
        return jsonPath("$.fieldErrors[?(@.field == '%s')].message", field).value(message);
    }
}
