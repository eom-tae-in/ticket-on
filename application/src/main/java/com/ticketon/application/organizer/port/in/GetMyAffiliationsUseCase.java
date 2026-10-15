package com.ticketon.application.organizer.port.in;

/**
 * 내 소속 목록 — 들어오는 포트. 주최자 전환에 쓴다: 한 사람이 여러 주최자에 소속되면 주최 관리에서 지금 일할 주최자를 고른다(docs/01 "운영자
 * 초대", docs/11 "구조 — 주최자 전환", docs/13 "3. 주최 관리"). 고른 주최자는 서버에 기억하지 않는다 — 주최 관리 요청이 늘 경로에 주최자 id를
 * 담고({@link ActingStaff}) 요청마다 권한을 확인한다. 읽기 전용이다.
 */
public interface GetMyAffiliationsUseCase {

    MyAffiliationsResult getMyAffiliations(GetMyAffiliationsQuery query);
}
