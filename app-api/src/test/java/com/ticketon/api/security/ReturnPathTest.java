package com.ticketon.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("로그인 뒤 돌아갈 경로 — 같은 사이트 안의 경로만 (열린 리다이렉트 방지)")
class ReturnPathTest {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "/",
            "/events/12/booking",
            "/events/12/booking?session=3&seats=A-1",
            "/events/12#seats",
            "/events/12/booking?next=//evil.example",
            "/events/%EA%B3%B5%EC%97%B0",
            "/events/공연",
            "/%2F%2Fevil.example"})
    @DisplayName("'/' 하나로 시작하는 같은 사이트 경로는 쿼리·# 뒤까지 그대로 돌려준다")
    void keepsSameSitePath(String path) {
        assertThat(ReturnPath.sameSiteOf(path)).contains(path);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "",
            "events/12",
            "https://evil.example/events/12",
            "javascript:alert(1)",
            "//evil.example",
            "///evil.example",
            "/\\evil.example",
            "/events\\12",
            "/\t/evil.example",
            "/\n/evil.example",
            "/\r/evil.example",
            " /events/12",
            "/events/12 ",
            "/events 12",
            "/　/evil.example",
            "/\u0000"})
    @DisplayName("다른 사이트로 읽힐 수 있는 값은 받지 않는다 — '//'·역슬래시·공백·제어 문자, '/'로 시작하지 않는 값")
    void rejectsPathsThatMayLeaveSite(String path) {
        assertThat(ReturnPath.sameSiteOf(path)).isEmpty();
    }

    @Test
    @DisplayName("요청에 경로가 없으면 비어 있다")
    void nullIsEmpty() {
        assertThat(ReturnPath.sameSiteOf(null)).isEmpty();
    }
}
