package com.ticketon.domain.error;

/**
 * 충돌(동시성 패배) — 같은 대상을 다른 요청이 먼저 바꿔서 이 요청이 졌다.
 * <p>
 * 예: 두 사람이 같은 좌석을 동시에 누르면 먼저 도달한 쪽이 갖고, 진 쪽은 "방금 다른 분이 선택했습니다"를 본다
 * (docs/06 "두 사람이 동시에 같은 자리를 눌렀다면"). 조건부 갱신이 기대한 줄 수를 바꾸지 못했을 때 던진다
 * (.claude/rules/persistence.md "동시성 — SQL에 드러나게"). 조용히 실패하지 않고 알려야 관객이 같은 자리를 계속 누르지 않는다.
 */
public final class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
