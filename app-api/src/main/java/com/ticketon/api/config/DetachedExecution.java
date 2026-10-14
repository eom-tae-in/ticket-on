package com.ticketon.api.config;

import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 응답과 떼어 실행하기 — 요청을 받은 스레드는 일을 맡기기만 하고 바로 응답한다. 맡긴 일은 작업 실행기의 다른 스레드에서 돈다
 * ({@link DetachedExecutionConfiguration}).
 * <p>
 * 일의 결과에 따라 응답이 달라지면 안 되는 요청에 쓴다. 비밀번호 찾기는 계정이 있을 때만 링크를 저장하고 메일을 보내는데, 요청 스레드에서
 * 하면 걸린 시간으로 가입 여부가 드러난다(docs/01 "같은 이메일로 양쪽 가입" — 가입 여부와 상관없이 같은 안내).
 * <p>
 * 맡긴 일이 실패해도 요청한 사람에게 알릴 길이 없다 — 알리면 결과가 드러난다. 그래서 실패는 일 이름과 함께 ERROR 로그로 남긴다.
 */
public class DetachedExecution {

    private static final Logger log = LoggerFactory.getLogger(DetachedExecution.class);

    private final Executor executor;

    public DetachedExecution(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * 일을 맡기고 바로 돌아온다. 일이 끝나기를 기다리지 않는다.
     *
     * @param taskName 실패했을 때 로그에 남길 일 이름. 이메일 같은 개인정보는 넣지 않는다.
     */
    public void run(String taskName, Runnable task) {
        Objects.requireNonNull(taskName, "taskName");
        Objects.requireNonNull(task, "task");
        executor.execute(() -> runLoggingFailure(taskName, task));
    }

    private static void runLoggingFailure(String taskName, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            log.error("응답과 떼어 실행한 일이 실패했습니다: {}", taskName, exception);
        }
    }
}
