package com.ticketon.application.event.query;

import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 한 주최자의 행사 한 줄 — 내 행사 목록(docs/13 "3. 주최 관리 — 내 행사 목록")이 구역을 나누고 카드를 그리는 데 쓸 칸만. 지운 행사는 담지 않는다.
 *
 * @param eventId        행사 id
 * @param title          제목
 * @param status         저장된 행사 상태 — 구역을 정한다({@code EventListSection})
 * @param wizardProgress 작성 진행 단계 — 작성 중에 저장한 가장 뒤의 단계
 * @param lastModifiedAt 마지막 수정 시각
 * @param poster         포스터 열쇠. 아직 올리지 않았으면 null
 * @param firstDate      일정의 첫 날짜 — 지우지 않은 날짜 중 가장 이른 날(한국 날짜). 날짜가 없으면 null
 * @param lastDate       일정의 마지막 날짜 — 지우지 않은 날짜 중 가장 늦은 날. 날짜가 없으면 null
 */
public record OrganizerEventSummary(long eventId, String title, EventStatus status, EventWizardStep wizardProgress,
        Instant lastModifiedAt, EventPoster poster, LocalDate firstDate, LocalDate lastDate) {

    public OrganizerEventSummary {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(wizardProgress, "wizardProgress");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
        if ((firstDate == null) != (lastDate == null) || (firstDate != null && firstDate.isAfter(lastDate))) {
            throw new IllegalArgumentException("일정의 첫 날짜와 마지막 날짜는 함께 있고 차례가 맞아야 합니다: 행사 " + eventId + ", "
                    + firstDate + " ~ " + lastDate);
        }
    }
}
