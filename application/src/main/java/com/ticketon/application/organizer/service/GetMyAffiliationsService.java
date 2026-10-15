package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.GetMyAffiliationsQuery;
import com.ticketon.application.organizer.port.in.GetMyAffiliationsUseCase;
import com.ticketon.application.organizer.port.in.MyAffiliationsResult;
import com.ticketon.application.organizer.port.out.OrganizerHomeQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 소속 목록 조회 — 읽기 전용이라 도메인을 거치지 않고 조회 포트가 돌려준 지금 소속(주최자명 · 역할 · 주최자 상태)을 옮긴다. 끝난 소속은
 * 고를 수 없어 담지 않는다(지난 소속은 운영진 프로필의 "참여한 주최자"에 나온다).
 */
@Service
public class GetMyAffiliationsService implements GetMyAffiliationsUseCase {

    private final OrganizerHomeQueryPort homeQueryPort;

    public GetMyAffiliationsService(OrganizerHomeQueryPort homeQueryPort) {
        this.homeQueryPort = homeQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public MyAffiliationsResult getMyAffiliations(GetMyAffiliationsQuery query) {
        return new MyAffiliationsResult(homeQueryPort.currentAffiliationsOf(query.account().id()).stream()
                .map(affiliation -> new MyAffiliationsResult.Affiliation(affiliation.organizerId(),
                        affiliation.organizerName(), affiliation.role(), affiliation.organizerStatus(),
                        affiliation.startedAt()))
                .toList());
    }
}
