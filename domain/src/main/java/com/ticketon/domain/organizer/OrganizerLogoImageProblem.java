package com.ticketon.domain.organizer;

import com.ticketon.domain.policy.ProposedPolicy;

/**
 * 올린 파일을 주최자 로고로 받을 수 없는 까닭({@link OrganizerLogoImage#problemOf}). 앞의 것부터 보고, 처음 걸린 것 하나만 답한다.
 */
public enum OrganizerLogoImageProblem {

    /** 빈 파일이다. */
    EMPTY,

    /** 용량 상한({@link ProposedPolicy#ORGANIZER_LOGO_MAXIMUM_SIZE}, 확인 필요)을 넘는다. */
    TOO_LARGE,

    /**
     * 받는 형식({@link ProposedPolicy#ORGANIZER_LOGO_ACCEPTED_FORMATS}, 확인 필요)이 아니다 — 올린 쪽이 적은 형식이 받는 형식이 아니거나, 실제
     * 바이트의 시그니처가 받는 형식이 아니거나, 둘이 서로 다르거나, 시그니처는 맞지만 그림 머리말이 깨져 크기를 읽을 수 없다.
     */
    UNSUPPORTED_FORMAT,

    /** 정사각형이 아니다 — 로고는 화면의 크롭 도구로 정사각형으로 잘라 올린다(docs/01 "공개 페이지 — 로고"). */
    NOT_SQUARE
}
