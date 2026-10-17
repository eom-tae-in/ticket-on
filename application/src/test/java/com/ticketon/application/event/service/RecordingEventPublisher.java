package com.ticketon.application.event.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

/** 발행한 애플리케이션 이벤트(재고에 알리는 값 등)를 차례대로 모아 두는 발행기. */
final class RecordingEventPublisher implements ApplicationEventPublisher {

    private final List<Object> published = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        published.add(event);
    }

    /** 발행한 차례대로. */
    List<Object> published() {
        return List.copyOf(published);
    }
}
