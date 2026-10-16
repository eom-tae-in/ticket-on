package com.ticketon.domain.event;

/**
 * 행사 공개 수준 3단계(docs/03 "공개 수준 3단계").
 * <table>
 *   <caption>공개 수준</caption>
 *   <tr><th>수준</th><th>목록 · 검색</th><th>접근</th></tr>
 *   <tr><td>공개</td><td>노출</td><td>누구나</td></tr>
 *   <tr><td>링크 전용</td><td>노출 안 됨</td><td>링크를 아는 사람</td></tr>
 *   <tr><td>암호 보호</td><td>노출 안 됨</td><td>링크 + 암호</td></tr>
 * </table>
 * 사내 행사, 동호회 내부 공연, 초청 전용 공연에 쓴다. 초대권으로 대신할 수 없다 — 초대권은 무료 발권이라 유료 내부 행사에 쓸 수 없다.
 * <p>
 * 링크는 어느 수준이든 추측할 수 없는 같은 공개 주소다({@link EventPublicAddress}). 비공개 행사도 주최자 페이지에 뜨지 않는다(docs/01
 * "목록에 띄우지 않는 행사"). 관리자는 비공개 행사도 전부 본다. 언제 바꿀 수 있는지(판매 종료 전까지)는 상태별 수정 범위가 정한다(docs/03 "전환",
 * docs/04 "상태별로 할 수 있는 것").
 * 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum EventVisibility {

    /** 공개 — 목록 · 검색에 뜨고 누구나 연다. 새 행사와 복제한 행사의 기본값이다(docs/04 "행사 복제 — 공개 수준은 기본값 공개로 시작"). */
    PUBLIC,

    /** 링크 전용 — 목록 · 검색에 뜨지 않고, 링크를 아는 사람이 연다. */
    LINK_ONLY,

    /** 암호 보호 — 목록 · 검색에 뜨지 않고, 링크를 알고 암호를 맞힌 사람이 연다. 암호는 해시로만 갖는다({@link EventPasswordHash}). */
    PASSWORD_PROTECTED;

    /**
     * 목록 · 검색에 뜨는 공개 수준인지 — 공개만 뜬다. 공개 수준만 놓고 본 것이다. 작성 중 · 삭제된 행사, 관리자가 내린 행사, 끝난 행사처럼 다른
     * 까닭으로 목록에서 빠지는 일은 따로 본다(docs/01 "목록에 띄우지 않는 행사", docs/04 "끝난 행사의 공개 페이지").
     */
    public boolean isListed() {
        return this == PUBLIC;
    }

    /** 열려면 암호가 필요한지 — 암호 보호만. */
    public boolean requiresPassword() {
        return this == PASSWORD_PROTECTED;
    }
}
