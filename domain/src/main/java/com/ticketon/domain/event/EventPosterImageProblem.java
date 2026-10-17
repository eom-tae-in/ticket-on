package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import com.ticketon.domain.policy.QuantityPolicy;

/**
 * 올린 파일을 행사 포스터로 받을 수 없는 까닭({@link EventPosterImage#problemOf}). 앞의 것부터 보고, 처음 걸린 것 하나만 답한다.
 */
public enum EventPosterImageProblem {

    /** 빈 파일이다. */
    EMPTY,

    /** 용량 상한({@link ProposedPolicy#EVENT_POSTER_MAXIMUM_SIZE}, 확인 필요)을 넘는다. */
    TOO_LARGE,

    /**
     * 받는 형식({@link ProposedPolicy#EVENT_POSTER_ACCEPTED_FORMATS}, 확인 필요)이 아니다 — 올린 쪽이 적은 형식이 받는 형식이 아니거나, 실제
     * 바이트의 시그니처가 받는 형식이 아니거나, 둘이 서로 다르거나, 시그니처는 맞지만 그림 머리말이 깨져 크기를 읽을 수 없다.
     */
    UNSUPPORTED_FORMAT,

    /**
     * 3:4 세로가 아니다({@link QuantityPolicy#POSTER_ASPECT_RATIO}, 허용 오차 {@link ProposedPolicy#EVENT_POSTER_ASPECT_RATIO_TOLERANCE}) — 포스터는
     * 화면의 크롭 도구로 3:4 세로로 잘라 올린다(docs/03 "기본 정보").
     */
    NOT_POSTER_ASPECT_RATIO
}
