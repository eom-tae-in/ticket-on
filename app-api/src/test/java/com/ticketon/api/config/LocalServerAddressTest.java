package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 내장 서버를 띄우는 통합 테스트의 서버 주소(#210). 통합 테스트는 {@code http://localhost:<포트>}로 요청한다. 서버가 모든 주소에 바인딩하면
 * macOS에서는 다른 프로그램이 같은 포트의 127.0.0.1을 쥘 수 있고, 그러면 요청이 그 프로그램으로 가서 엉뚱한 404를 받는다.
 * 테스트 설정({@code src/test/resources/application.properties})이 서버를 localhost에만 바인딩하는지, DB 없이 서버만 띄워 확인한다.
 */
@SpringBootTest(classes = LocalServerAddressTest.WebServerOnly.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@DisplayName("통합 테스트의 내장 서버 주소 — 테스트 요청이 다른 프로그램으로 새지 않는다")
class LocalServerAddressTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("서버가 테스트 요청 주소(localhost)를 직접 쥐어, 다른 프로그램이 같은 포트의 그 주소에 바인딩할 수 없다")
    void otherProgramCannotTakeRequestAddress() throws IOException {
        InetSocketAddress requestAddress = new InetSocketAddress(InetAddress.getByName("localhost"), port);

        try (ServerSocketChannel otherProgram = ServerSocketChannel.open()) {
            assertThatExceptionOfType(BindException.class).isThrownBy(() -> otherProgram.bind(requestAddress));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(TomcatServletWebServerAutoConfiguration.class)
    static class WebServerOnly {
    }
}
