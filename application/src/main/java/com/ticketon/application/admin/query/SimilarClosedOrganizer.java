package com.ticketon.application.admin.query;

import java.util.Objects;

/**
 * 새 주최자 신청의 심사 화면에 함께 보이는 닫힌 주최자 하나 — 같은 소유자이거나 같은 단체명이다(docs/01 "닫힌 주최자를 다시 열 때").
 * <p>
 * 닫고 같은 이름으로 새로 신청하면 이력이 하나도 없는 새 주최자가 된다. 이런 이력 세탁을 막으려고 관리자에게 보여 주고, 같은 단체로 보이면
 * 관리자가 "다시 열기"로 돌린다(돌리기는 #134).
 *
 * @param organizerId   닫힌 주최자 id
 * @param organizerName 닫힌 주최자명
 * @param sameOwner     신청한 사람이 이 주최자의 소유자였는지
 * @param sameName      주최자명이 신청한 이름 또는 단체명과 같은지
 */
public record SimilarClosedOrganizer(long organizerId, String organizerName, boolean sameOwner, boolean sameName) {

    public SimilarClosedOrganizer {
        Objects.requireNonNull(organizerName, "organizerName");
        if (!sameOwner && !sameName) {
            throw new IllegalArgumentException("같은 소유자도 같은 단체명도 아닌 닫힌 주최자입니다: " + organizerId);
        }
    }
}
