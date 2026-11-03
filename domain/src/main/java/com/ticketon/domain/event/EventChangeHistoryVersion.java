package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 변경 이력에 새로 쌓을 버전 하나 — 한 대상 칸이 이 내용으로 바뀌었다(docs/03 "버전별로 기간과 함께 보관합니다"). 어떤 버전을 쌓는지는
 * {@link EventChangeHistory}가 정한다.
 * <p>
 * 버전은 덮어쓰지 않고 쌓는다. 새 버전을 쌓으면 그 칸의 지금 버전은 그 순간에 유효 기간이 끝나고, 새 버전은 그 순간부터 다음 버전이 쌓일 때까지
 * 유효하다 — "3/1~3/10 유효"처럼 예매 시각과 대조하면 관객이 어느 버전을 보고 샀는지 안다. 유효 기간 · 바꾼 사람 · 통지 발송 여부는 쌓는 쪽이
 * 함께 남긴다.
 * <ul>
 *   <li><b>첫 버전</b> — 판매를 시작해 공개된 순간의 내용이다. 바꾸기 전이 없다. 출연이 적힌 회차만 쌓는다
 *       ({@link EventChangeHistory#firstVersions}).</li>
 *   <li><b>바뀐 버전</b> — 바꾸기 전과 바꾼 뒤가 다르다. 같으면 바뀐 것이 아니라 쌓지 않는다.</li>
 * </ul>
 *
 * @param target          대상 칸
 * @param sessionId       회차 출연이면 그 회차의 id. 행사에 하나인 칸이면 null
 * @param previousContent 바꾸기 전 내용. 첫 버전이거나, 비어 있던 회차 출연을 채웠으면 null
 * @param newContent      바꾼 뒤 내용 — 제목 · 소개는 글 그대로, 포스터는 파일 열쇠, 출연은 한 줄 그대로. 회차 출연을 비웠으면 null
 */
public record EventChangeHistoryVersion(EventChangeHistoryTarget target, Long sessionId, String previousContent,
        String newContent) {

    /**
     * @throws IllegalArgumentException 회차 출연인데 회차가 없거나 행사에 하나인 칸인데 회차가 있으면, 회차 id가 1보다 작으면, 비울 수 없는 칸의
     *                                  바꾼 뒤 내용이 없으면, 바꾸기 전과 바꾼 뒤가 같으면(첫 버전의 내용이 없는 경우 포함)
     */
    public EventChangeHistoryVersion {
        Objects.requireNonNull(target, "target");
        if (target.isPerSession() != (sessionId != null)) {
            throw new IllegalArgumentException("회차 출연만 회차를 가리킵니다: " + target + ", 회차 " + sessionId);
        }
        if (sessionId != null && sessionId < 1) {
            throw new IllegalArgumentException("회차 id는 1 이상이어야 합니다: " + sessionId);
        }
        if (newContent == null && !target.allowsEmptyContent()) {
            throw new IllegalArgumentException(target + "는 비울 수 없습니다");
        }
        if (Objects.equals(previousContent, newContent)) {
            throw new IllegalArgumentException("바뀌지 않은 내용은 새 버전이 아닙니다: " + target);
        }
    }

    /** 첫 버전 — 행사에 하나인 칸이 판매를 시작해 공개된 순간의 내용. */
    static EventChangeHistoryVersion first(EventChangeHistoryTarget target, String content) {
        return new EventChangeHistoryVersion(target, null, null, content);
    }

    /** 첫 버전 — 판매를 시작해 공개된 순간 그 회차에 적혀 있던 출연. */
    static EventChangeHistoryVersion firstOfSession(long sessionId, String performers) {
        return new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, sessionId, null, performers);
    }
}
