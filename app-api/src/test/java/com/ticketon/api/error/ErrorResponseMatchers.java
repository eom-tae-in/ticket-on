package com.ticketon.api.error;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultMatcher;

/** MockMvc 응답이 {@link ErrorResponse} 모양인지 본다. */
public final class ErrorResponseMatchers {

    private ErrorResponseMatchers() {
    }

    /** 입력 오류가 아닌 오류 응답 — JSON 본문의 code·message가 같고 fieldErrors는 빈 배열이다. */
    public static ResultMatcher errorBody(String code, String message) {
        return result -> {
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.message").value(message).match(result);
            jsonPath("$.fieldErrors").isArray().match(result);
            jsonPath("$.fieldErrors").isEmpty().match(result);
        };
    }
}
