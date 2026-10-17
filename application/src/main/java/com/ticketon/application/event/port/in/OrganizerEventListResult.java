package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 내 행사 목록 — 세 구역(docs/13 "3. 주최 관리 — 내 행사 목록"). 화면 문구("마지막 수정 3일 전", "4/7 단계 · 가격 설정까지 완료")는 화면이 만들고,
 * 여기는 시각 · 단계 · 날짜 값을 준다.
 * <ul>
 *   <li>작성 중 — 마지막 수정이 최근인 것부터.</li>
 *   <li>진행 중 행사(판매 대기 · 판매 중 · 판매 중단 · 판매 종료) — 첫 날짜가 이른 것부터.</li>
 *   <li>지난 행사(행사 종료 · 정산 완료 · 취소됨) — 마지막 날짜가 늦은 것부터.</li>
 * </ul>
 * 차례가 같으면 행사 id로 가른다(작성 중 · 지난 행사는 큰 쪽, 진행 중은 작은 쪽부터). 날짜가 없는 행사는 그 구역의 끝에 둔다.
 *
 * @param drafts     작성 중
 * @param inProgress 진행 중 행사
 * @param past       지난 행사
 */
public record OrganizerEventListResult(List<DraftEvent> drafts, List<ListedEvent> inProgress, List<ListedEvent> past) {

    public OrganizerEventListResult {
        drafts = List.copyOf(drafts);
        inProgress = List.copyOf(inProgress);
        past = List.copyOf(past);
    }

    /**
     * 작성 중 행사 한 장(docs/04 "작성 중 행사 관리") — 진행률, 마지막 수정일, 멈춘 단계로 바로 가는 버튼.
     *
     * @param eventId        행사 id
     * @param title          제목
     * @param posterUrl      포스터의 공개 주소. 아직 올리지 않았으면 null
     * @param completedStep  작성 진행 단계 — 저장한 가장 뒤의 단계("4/7 단계 · 가격 설정까지 완료"의 4단계)
     * @param resumeStep     이어서 할 단계 — [이어서 작성]이 여는 단계
     * @param lastModifiedAt 마지막 수정 시각
     * @param firstDate      일정의 첫 날짜. 날짜가 없으면 null
     * @param lastDate       일정의 마지막 날짜. 날짜가 없으면 null
     * @param folded         목록에서 접어 둘지 — 일정의 날짜가 모두 지났다. 화면은 접어 두고 정리를 안내한다
     */
    public record DraftEvent(long eventId, String title, String posterUrl, EventWizardStep completedStep, EventWizardStep resumeStep,
            Instant lastModifiedAt, LocalDate firstDate, LocalDate lastDate, boolean folded) {

        public DraftEvent {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(completedStep, "completedStep");
            Objects.requireNonNull(resumeStep, "resumeStep");
            Objects.requireNonNull(lastModifiedAt, "lastModifiedAt");
        }
    }

    /**
     * 진행 중 · 지난 행사 한 장.
     *
     * @param eventId   행사 id
     * @param title     제목
     * @param posterUrl 포스터의 공개 주소. 없으면 null
     * @param status    저장된 행사 상태
     * @param firstDate 일정의 첫 날짜. 날짜가 없으면 null
     * @param lastDate  일정의 마지막 날짜. 날짜가 없으면 null
     */
    public record ListedEvent(long eventId, String title, String posterUrl, EventStatus status, LocalDate firstDate,
            LocalDate lastDate) {

        public ListedEvent {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(status, "status");
        }
    }
}
