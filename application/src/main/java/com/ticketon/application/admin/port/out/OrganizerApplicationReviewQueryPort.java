package com.ticketon.application.admin.port.out;

import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.domain.organizer.OrganizerName;
import java.util.List;

/**
 * 주최자 심사 화면의 이력 세탁 대조 — 나가는 포트. 읽기 전용이라 도메인 객체를 만들지 않는다.
 * <p>
 * 새 신청이 들어오면 심사 화면에 같은 소유자 또는 같은 단체명의 닫힌 주최자를 함께 보여 준다(docs/01 "닫힌 주최자를 다시 열 때",
 * docs/99 B "닫힌 주최자 이름으로 새 주최자 신청"). 닫힌 주최자만 본다 — 심사 중 · 활동 · 영구 정지 주최자는 나오지 않는다.
 */
public interface OrganizerApplicationReviewQueryPort {

    /**
     * 같은 소유자 또는 같은 단체명의 닫힌 주최자 — 주최자 id 순.
     * <ul>
     *   <li>같은 소유자 — 신청한 사람이 그 주최자에 소유자로 소속된 적이 있다(지난 소속 포함).</li>
     *   <li>같은 단체명 — 주최자명이 신청한 이름과 글자 그대로 같다(둘 다 앞뒤 공백을 걷고 NFC로 맞춘 값).</li>
     * </ul>
     *
     * @param applicantStaffAccountId 신청한 사람의 운영진 계정 id
     * @param name                    신청한 이름 또는 단체명
     */
    List<SimilarClosedOrganizer> similarClosedOrganizers(long applicantStaffAccountId, OrganizerName name);
}
