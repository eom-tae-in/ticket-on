package com.ticketon.message;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * 한 클래스의 로거가 남긴 로그를 붙잡는다. 테스트가 끝나면 {@link #close()}로 떼어 낸다.
 */
final class CapturedLogs implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLogs(Class<?> loggingClass) {
        this.logger = (Logger) LoggerFactory.getLogger(loggingClass);
        appender.start();
        logger.addAppender(appender);
    }

    static CapturedLogs of(Class<?> loggingClass) {
        return new CapturedLogs(loggingClass);
    }

    List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
