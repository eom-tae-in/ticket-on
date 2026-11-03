package com.ticketon.application.event.query;

import com.ticketon.domain.event.EventChangeHistoryTarget;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 행사 변경 이력의 버전 한 줄 — 관리자가 신고를 확인할 때 보는 칸(docs/03 "이력에 남길 것": 변경 일시 · 변경 전 내용 · 변경 후 내용 · 통지 발송
 * 여부 · 누가 바꿨는지).
 * <p>
 * 유효 기간은 [{@code validFrom}, {@code validUntil})이다 — 끝 순간에는 다음 버전이 유효하다. 예매 시각을 넣어 그 순간에 보이던 버전인지 본다
 * ({@link #isValidAt}): "3/1 '주연: 김배우' 등록, 3/5 예매, 3/10 '주연: 이배우'로 수정"이면 3/5에는 김배우 버전이 유효했다.
 * <p>
 * 공개용 연락처의 줄은 {@link #toString}에 내용을 싣지 않는다({@link EventChangeHistoryTarget#masksContent}). 같은지는 값으로 본다.
 *
 * @param id                      이력 줄 id — 같은 순간에 쌓인 버전의 차례
 * @param target                  대상 칸
 * @param sessionId               회차 출연이면 그 회차의 id. 아니면 null
 * @param sessionDate             회차 출연이면 그 회차의 지금 날짜(한국 날짜) — 관리자가 회차를 알아보게. 아니면 null
 * @param sessionStartTime        회차 출연이고 회차형이면 그 회차의 지금 시작 시각. 종일형이거나 회차 출연이 아니면 null
 * @param previousContent         바꾸기 전 내용. 첫 버전이거나 비어 있던 선택 칸(상세 안내 · 지도 링크 · 공개용 연락처 · 출연)을 채웠으면 null
 * @param newContent              바꾼 뒤 내용 — 포스터는 파일 열쇠, 지도 링크는 주소. 선택 칸을 비웠으면 null
 * @param validFrom               유효 시작 — 변경 일시
 * @param validUntil              유효 끝 — 다음 버전이 쌓인 순간. 지금 버전이면 null
 * @param changedByStaffAccountId 바꾼 운영진의 계정 id
 * @param changedByNickname       바꾼 그때의 운영진 닉네임
 * @param noticeSent              예매자에게 알렸는지(통지 발송 여부)
 */
public record EventChangeHistoryEntry(long id, EventChangeHistoryTarget target, Long sessionId, LocalDate sessionDate,
        LocalTime sessionStartTime, String previousContent, String newContent, Instant validFrom, Instant validUntil,
        long changedByStaffAccountId, String changedByNickname, boolean noticeSent) {

    public EventChangeHistoryEntry {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(changedByNickname, "changedByNickname");
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("유효 끝이 시작보다 앞섭니다: 이력 " + id + ", " + validFrom + " ~ " + validUntil);
        }
    }

    /** 그 순간에 이 버전이 보였는지 — 유효 시작 이후(같음 포함)이고 유효 끝 전이다. */
    public boolean isValidAt(Instant moment) {
        Objects.requireNonNull(moment, "moment");
        return !moment.isBefore(validFrom) && (validUntil == null || moment.isBefore(validUntil));
    }

    @Override
    public String toString() {
        return "EventChangeHistoryEntry[id=" + id + ", target=" + target + ", sessionId=" + sessionId + ", sessionDate=" + sessionDate
                + ", sessionStartTime=" + sessionStartTime + ", previousContent=" + shown(previousContent) + ", newContent="
                + shown(newContent) + ", validFrom=" + validFrom + ", validUntil=" + validUntil + ", changedByStaffAccountId="
                + changedByStaffAccountId + ", changedByNickname=" + changedByNickname + ", noticeSent=" + noticeSent + "]";
    }

    private String shown(String content) {
        return content != null && target.masksContent() ? "****" : content;
    }
}
