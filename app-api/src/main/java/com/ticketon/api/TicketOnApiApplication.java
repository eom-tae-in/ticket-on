package com.ticketon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API 서버 실행 지점.
 * <p>
 * com.ticketon 전체를 스캔해야 runtimeOnly로 묶인 infra-* 어댑터가 포트에 연결된다.
 */
@SpringBootApplication(scanBasePackages = "com.ticketon")
public class TicketOnApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketOnApiApplication.class, args);
    }
}
