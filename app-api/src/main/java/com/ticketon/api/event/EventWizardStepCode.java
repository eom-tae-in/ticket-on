package com.ticketon.api.event;

import com.ticketon.domain.event.EventWizardStep;

/**
 * 행사 만들기 위저드 단계의 API 이름 — 화면 타입에 쓰인다(docs/03 "행사 만들기 위저드" 7단계). 작성 중 행사 목록의 진행률과 [이어서 작성]이 멈춘
 * 단계를 이것으로 안다(docs/04 "작성 중 행사 관리").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 단계({@link EventWizardStep})와 같다.
 */
public enum EventWizardStepCode {

    /** 1단계 — 유형 · 제목 · 소개 · 포스터 · 장소 · 카테고리. */
    BASIC_INFORMATION(EventWizardStep.BASIC_INFORMATION),

    /** 2단계 — 회차 방식 · 회차. */
    SCHEDULE(EventWizardStep.SCHEDULE),

    /** 3단계 — 블록 · 구역 · 좌석 배치도 또는 정원. */
    SEATING(EventWizardStep.SEATING),

    /** 4단계 — 가격 · 조기예매 · 쿠폰 · 묶음권. */
    PRICING(EventWizardStep.PRICING),

    /** 5단계 — 공개 수준 · 오픈/종료 · 매수 제한 · 재입장 · 검표 시작 · 연령 제한 · 취소 규정 조정. */
    BOOKING_SETTINGS(EventWizardStep.BOOKING_SETTINGS),

    /** 6단계 — 입구 목록 · 검표 계정. */
    ENTRANCES_AND_CHECKIN_ACCOUNTS(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS),

    /** 7단계 — 검토 · 관객 화면 미리보기 → 판매 시작. */
    REVIEW(EventWizardStep.REVIEW);

    private final EventWizardStep step;

    EventWizardStepCode(EventWizardStep step) {
        this.step = step;
    }

    static EventWizardStepCode from(EventWizardStep step) {
        for (EventWizardStepCode code : values()) {
            if (code.step == step) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 위저드 단계: " + step);
    }
}
