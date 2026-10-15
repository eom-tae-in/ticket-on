package com.ticketon.api.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 가짜 카카오·구글 — 토큰 주소와 사용자 정보 주소만 흉내 내는 작은 HTTP 서버(JDK 내장 HttpServer). 실제 제공자 없이 로그인 흐름을 끝까지 돌린다.
 * <p>
 * 인가 주소({@code /{kakao|google}/authorize})는 관객 브라우저가 가는 곳이라 이 서버에는 오지 않는다. 테스트는 로그인 시작이 돌려준 주소에서
 * state를 꺼내고, 제공자가 관객을 돌려보내는 것처럼 콜백 주소를 직접 부른다(인가 코드는 {@link #willAuthenticate}로 정한 값).
 * <ul>
 *   <li>토큰 주소 {@code POST /{제공자}/token} — 정해 둔 인가 코드면 {@code token-<코드>} 토큰을 준다. 모르는 코드면 400 invalid_grant.</li>
 *   <li>사용자 정보 주소 {@code GET /{제공자}/userinfo} — 그 토큰의 인가 코드에 정해 둔 사용자 정보 JSON을 준다. 모르는 토큰이면 401.</li>
 * </ul>
 * 받은 토큰 요청은 {@link #tokenRequests()}로 꺼내 클라이언트 인증 방식(카카오 본문 시크릿, 구글 Basic)을 볼 수 있다.
 */
final class FakeOAuthProvider implements AutoCloseable {

    static final String KAKAO_CLIENT_ID = "test-kakao-client";

    static final String KAKAO_CLIENT_SECRET = "test-kakao-secret";

    static final String GOOGLE_CLIENT_ID = "test-google-client";

    static final String GOOGLE_CLIENT_SECRET = "test-google-secret";

    private static final String TOKEN_PREFIX = "token-";

    private final HttpServer server;
    private final Map<String, String> userInfoByCode = new ConcurrentHashMap<>();
    private final List<TokenRequest> tokenRequests = Collections.synchronizedList(new ArrayList<>());

    private FakeOAuthProvider(HttpServer server) {
        this.server = server;
    }

    static FakeOAuthProvider start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeOAuthProvider provider = new FakeOAuthProvider(server);
            for (String registrationId : List.of("kakao", "google")) {
                server.createContext("/" + registrationId + "/token", exchange -> provider.token(registrationId, exchange));
                server.createContext("/" + registrationId + "/userinfo", provider::userInfo);
            }
            server.start();
            return provider;
        } catch (IOException exception) {
            throw new IllegalStateException("가짜 OAuth 제공자를 띄우지 못했습니다", exception);
        }
    }

    String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    /** 카카오·구글 로그인 설정을 이 서버로 바꾼다 — 클라이언트 id·시크릿은 시험용 값, 주소는 이 서버. */
    void registerProperties(DynamicPropertyRegistry registry) {
        register(registry, "kakao", KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET);
        register(registry, "google", GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET);
    }

    /** 이 인가 코드로 돌아온 관객을 이 사용자 정보의 사용자로 인증한다. */
    void willAuthenticate(String code, String userInfoJson) {
        userInfoByCode.put(code, userInfoJson);
    }

    List<TokenRequest> tokenRequests() {
        return List.copyOf(tokenRequests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void register(DynamicPropertyRegistry registry, String registrationId, String clientId, String clientSecret) {
        String prefix = "ticketon.oauth." + registrationId + ".";
        registry.add(prefix + "client-id", () -> clientId);
        registry.add(prefix + "client-secret", () -> clientSecret);
        registry.add(prefix + "authorization-uri", () -> baseUrl() + "/" + registrationId + "/authorize");
        registry.add(prefix + "token-uri", () -> baseUrl() + "/" + registrationId + "/token");
        registry.add(prefix + "user-info-uri", () -> baseUrl() + "/" + registrationId + "/userinfo");
    }

    private void token(String registrationId, HttpExchange exchange) throws IOException {
        Map<String, String> form = formOf(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        tokenRequests.add(new TokenRequest(registrationId, form, exchange.getRequestHeaders().getFirst("Authorization")));
        String code = form.get("code");
        if (code == null || !userInfoByCode.containsKey(code)) {
            respond(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        respond(exchange, 200, "{\"access_token\":\"" + TOKEN_PREFIX + code + "\",\"token_type\":\"bearer\",\"expires_in\":3600}");
    }

    private void userInfo(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String bearerPrefix = "Bearer " + TOKEN_PREFIX;
        String code = authorization != null && authorization.startsWith(bearerPrefix)
                ? authorization.substring(bearerPrefix.length()) : null;
        if (code == null || !userInfoByCode.containsKey(code)) {
            respond(exchange, 401, "{\"error\":\"invalid_token\"}");
            return;
        }
        respond(exchange, 200, userInfoByCode.get(code));
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static Map<String, String> formOf(String body) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                form.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
            }
        }
        return form;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    /**
     * 받은 토큰 요청 하나.
     *
     * @param registrationId 제공자 등록 id
     * @param form           요청 본문의 칸
     * @param authorization  Authorization 머리글. 없으면 null.
     */
    record TokenRequest(String registrationId, Map<String, String> form, String authorization) {
    }
}
