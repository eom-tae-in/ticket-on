package com.ticketon.application.event.query;

import com.ticketon.domain.event.EventWizardStep;
import java.time.Instant;
import java.util.Objects;

/**
 * 30일 미수정 알림을 보낼지 볼 작성 중 행사 하나 — 알림을 판정하고 메일을 쓰는 데 필요한 칸만(docs/04 "작성 중 행사 관리", docs/12 "주최자 대상 —
 * 작성 중 행사 30일 미수정").
 *
 * @param eventId        행사 id
 * @param organizerId    그 행사를 여는 주최자 id — 알림은 이 주최자의 운영진 전원에게 간다
 * @param organizerName  주최자명 — 여러 주최자에 소속된 운영진이 어느 주최자의 행사인지 알게 메일에 적는다
 * @param title          행사 제목
 * @param wizardProgress 작성 진행 단계
 * @param lastModifiedAt 마지막 수정 시각 — 미수정 구간을 가린다
 */
public record IdleDraftEvent(long eventId, long organizerId, String organizerName, String title, EventWizardStep wizardProgress,
        Instant lastModifiedAt) {

    public IdleDraftEvent {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(wizardProgress, "wizardProgress");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
    }
}
