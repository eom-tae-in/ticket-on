package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 응답과 떼어 실행하기 — Spring Boot의 애플리케이션 작업 실행기로 묶여, 맡긴 일이 부른 스레드가 아닌 곳에서 돌고, 부른 쪽은 일이 끝나기를
 * 기다리지 않으며, 실패는 로그로만 남는지 본다. DB 없이 돈다.
 */
@SpringJUnitConfig(DetachedExecutionConfiguration.class)
@ImportAutoConfiguration(TaskExecutionAutoConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("응답과 떼어 실행하기")
class DetachedExecutionTest {

    @Autowired
    private DetachedExecution detachedExecution;

    @Test
    @DisplayName("맡긴 일은 부른 스레드가 아닌 작업 실행기 스레드에서 돈다")
    void runsOnAnotherThread() throws Exception {
        CompletableFuture<Thread> worker = new CompletableFuture<>();

        detachedExecution.run("스레드 확인", () -> worker.complete(Thread.currentThread()));

        assertThat(worker.get(5, TimeUnit.SECONDS)).isNotSameAs(Thread.currentThread());
    }

    @Test
    @DisplayName("부른 쪽은 맡긴 일이 끝나기를 기다리지 않고 바로 돌아온다")
    void returnsBeforeTaskFinishes() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        detachedExecution.run("오래 걸리는 일", () -> {
            awaitQuietly(release);
            finished.countDown();
        });

        assertThat(finished.getCount()).isEqualTo(1);
        release.countDown();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("맡긴 일이 실패해도 부른 쪽에는 예외가 오지 않고, 일 이름과 함께 ERROR 로그로 남는다")
    void logsFailure(CapturedOutput output) throws Exception {
        CountDownLatch failed = new CountDownLatch(1);

        detachedExecution.run("실패하는 일", () -> {
            failed.countDown();
            throw new IllegalStateException("메일 업체 응답 없음");
        });

        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
        awaitLog(output, "응답과 떼어 실행한 일이 실패했습니다: 실패하는 일");
        assertThat(output.getOut()).contains("ERROR").contains("메일 업체 응답 없음");
    }

    /** 다른 스레드가 남기는 로그는 조금 늦게 찍힐 수 있어 잠깐 기다린다. */
    private static void awaitLog(CapturedOutput output, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!output.getOut().contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(output.getOut()).contains(expected);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("기다리던 중 끊겼습니다", exception);
        }
    }
}
