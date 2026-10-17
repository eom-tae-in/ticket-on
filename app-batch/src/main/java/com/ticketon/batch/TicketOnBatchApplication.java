package com.ticketon.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 정해진 시각 작업 실행 지점 (전날 리마인더, 정산 기한, 보관 기간 지난 기록 삭제 등).
 * <p>
 * 작업 클래스는 들어오는 어댑터다. 유스케이스(들어오는 포트)를 부르기만 하고 규칙을 갖지 않는다. 스케줄러는
 * {@link com.ticketon.batch.config.SchedulingConfiguration}이 켠다.
 */
@SpringBootApplication(scanBasePackages = "com.ticketon")
public class TicketOnBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketOnBatchApplication.class, args);
    }
}
